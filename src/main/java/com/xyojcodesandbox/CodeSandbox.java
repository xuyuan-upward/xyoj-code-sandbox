package com.xyojcodesandbox;


import com.xyojcodesandbox.model.ExecuteCodeRequest;
import com.xyojcodesandbox.model.ExecuteCodeResponse;
/**
 * 代码沙箱接口定义
 */
public interface CodeSandbox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
