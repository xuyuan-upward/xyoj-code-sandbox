package com.xyojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import com.sun.org.apache.regexp.internal.RE;
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
 * java本地沙箱
 */
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate implements CodeSandbox{
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
