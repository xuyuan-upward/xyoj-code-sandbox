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
public class ExecuteCodeResponse {
    /**
     * 接口信息
     */
    private String message;
    /**
     * 编译状态
     */
    private Integer status;
    /**
     * 编译信息
     */
    private JudgeInfo judgeInfo;

    /**
     * 结果
     */
    private List<String> outputList;
}
