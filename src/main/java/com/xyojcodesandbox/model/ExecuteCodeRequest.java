package com.xyojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteCodeRequest {
    /**
     * 代码输入测试用例
     */
    private List<String> input;
    /**
     * 代码内容
     */
    private String code;
    /**
     * 编程语言
     */
    private String language;
}
