package com.xyojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.xyojcodesandbox.model.ExecuteCodeRequest;
import com.xyojcodesandbox.model.ExecuteCodeResponse;
import com.xyojcodesandbox.model.ExecuteMessage;
import com.xyojcodesandbox.model.JudgeInfo;
import com.xyojcodesandbox.utils.ProcessUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 模板方法优化
 */
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;
    /**
     * 保存文件
     * @param code
     * @return
     */
    public File saveFile(String code) {

        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File UserCodePathFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return UserCodePathFile;
    }

    /**
     * 编译文件
     * @param UserCodePathFile
     * @return
     */
    public ExecuteMessage compileFile(File UserCodePathFile) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        // 2.编译代码 得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s", UserCodePathFile.getAbsolutePath());
        try {
            //
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
             executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return executeMessage;
    }

    /**
     * 运行文件
     * @param inputList
     * @param UserCodePathFile
     * @return
     */
    public List<ExecuteMessage> runFile(List<String> inputList,File UserCodePathFile) {
        String userCodeParentPath = UserCodePathFile.getParent();
        ArrayList<ExecuteMessage> executeList = new ArrayList<>();
        for (String inputArg : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArg);
            // 得出运行后的结果
            ExecuteMessage executeMessage = new ExecuteMessage();
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
            executeList.add(executeMessage);
        }
        return executeList;
    }

    /**
     * 处理结果
     * @param executeList
     * @param inputList
     * @return
     */
    public ExecuteCodeResponse resultHandle(List<ExecuteMessage> executeList,List<String> inputList) {
        long maxTime = 0;
        ArrayList<String> outputList = new ArrayList<>();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        for (ExecuteMessage executeMessage : executeList) {
            // 4.1输出的结果
            String outputMessage = executeMessage.getMessage();
            String errorMessage = executeMessage.getErrorMessage();
            Long time = executeMessage.getTime();
            // todo 内存java本地不好获取，需要借助第三方库 先不做实现
            // Long memory = executeMessage.getMemory();
            if (StringUtils.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                break;
            }
            outputList.add(outputMessage);
            // 4.2获取测试用例的最大消耗时间
            maxTime = Math.max(maxTime, time);
        }
        // 4.3判断运行是否一致
        if (inputList.size() != outputList.size()) {
            executeCodeResponse.setStatus(3);
        } else {
            executeCodeResponse.setStatus(2);
        }
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage("代码执行成功");
        judgeInfo.setMemory(0L);
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setMessage("代码沙箱整体运行成功");
        return executeCodeResponse;
    }
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInput();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        // 1.把用户的代码隔离保存（保存文件）
        File UserCodePathFile = saveFile(code);
        // 2.编译代码 得到class文件
        ExecuteMessage executeMessage1 = compileFile(UserCodePathFile);
        // 3.运行代码
        List<ExecuteMessage> executeMessageList = runFile(inputList, UserCodePathFile);
        // 4.收集并进行结果处理
        ExecuteCodeResponse executeCodeResponse = resultHandle(executeMessageList, inputList);
        // 5.删除文件
        FileUtil.del(UserCodePathFile.getParent());
        return executeCodeResponse;
    }
}
