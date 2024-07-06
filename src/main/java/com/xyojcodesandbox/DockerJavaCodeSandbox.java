package com.xyojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.xyojcodesandbox.model.ExecuteCodeRequest;
import com.xyojcodesandbox.model.ExecuteCodeResponse;
import com.xyojcodesandbox.model.ExecuteMessage;
import com.xyojcodesandbox.model.JudgeInfo;
import com.xyojcodesandbox.utils.ProcessUtils;
import jdk.nashorn.internal.ir.IfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.Host;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * java本地沙箱
 */
@Slf4j
public class DockerJavaCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox{

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 运行超时时间
     */
    private static final long TIME_OUT = 5000L;
    private boolean isPullImag = false;

    @Override
    public List<ExecuteMessage> runFile(List<String> inputList, File UserCodePathFile) {
        String userCodeParentPath = UserCodePathFile.getParent();
        // 3.todo Docker容器隔离运行代码
        // 3.1创建容器
        //  3.1.1获取docker客户端实例
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 3.1.2拉取镜像
        String Image = "openjdk:8-alpine";
        if (!isPullImag) {
            // 拉取镜像是异步的
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(Image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback)
                        // 等待镜像拉取完成
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        log.info("下载完成");

        // 3.1.3 创建容器并启动 得到创建容器的命令
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(Image);
        // 配置了容器的主机配置(HostConfig)
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(150 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        // 执行创建容器
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        String containerId = createContainerResponse.getId();
        dockerClient.startContainerCmd(containerId).exec();
        log.info("创建容器启动执行命令",createContainerResponse);
        // 执行命令并获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;
            // 判断是否超时
            final boolean[] timeout = {true};
            // 得到创建命令的Id
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    // 如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + message[0]);
                    }
                    super.onNext(frame);
                }
            };

            final long[] maxMemory = {0L};

            // 获取占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemory[0]);
                }

                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            });
            statsCmd.exec(statisticsResultCallback);
            try {
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MICROSECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }

        return executeMessageList;
    }
}


//        ArrayList<ExecuteMessage> executeList = new ArrayList<>();
//        for (String inputArg : inputArgs) {
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArg);
//            // 得出运行后的结果
//            ExecuteMessage executeMessage = new ExecuteMessage();
//            try {
//                Process runProcess = Runtime.getRuntime().exec(runCmd);
//                executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
//                System.out.println(executeMessage);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            executeList.add(executeMessage);
//        }
//        // 4.收集并进行结果处理
//        long maxTime = 0;
//        ArrayList<String> outputList = new ArrayList<>();
//        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
//        for (ExecuteMessage executeMessage : executeList) {
//            // 4.1输出的结果
//            String outputMessage = executeMessage.getMessage();
//            String errorMessage = executeMessage.getErrorMessage();
//            Long time = executeMessage.getTime();
//            // todo 内存java本地不好获取，需要借助第三方库 先不做实现
//            // Long memory = executeMessage.getMemory();
//            if (StringUtils.isNotBlank(errorMessage)) {
//                executeCodeResponse.setMessage(errorMessage);
//                break;
//            }
//            outputList.add(outputMessage);
//            // 4.2获取测试用例的最大消耗时间
//            maxTime = Math.max(maxTime,time);
//        }
//        // 4.3判断运行是否一致
//        if (inputArgs.size() != outputList.size()) {
//            executeCodeResponse.setStatus(3);
//        }else{
//            executeCodeResponse.setStatus(2);
//        }
//        // 5.删除文件
//        FileUtil.del(userCodeParentPath);
//        // 6.返回结果
//        JudgeInfo judgeInfo = new JudgeInfo();
//        judgeInfo.setMessage("代码执行成功");
//        judgeInfo.setMemory(0L);
//        judgeInfo.setTime(maxTime);
//        executeCodeResponse.setJudgeInfo(judgeInfo);
//        executeCodeResponse.setOutputList(outputList);
//        executeCodeResponse.setMessage("代码沙箱整体运行成功");
//        return executeCodeResponse;