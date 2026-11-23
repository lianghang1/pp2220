package com.sfi.conflux.pay.example.web;

import com.sfi.conflux.common.exception.CommonErrorCode;
import com.sfi.conflux.pay.exception.GatewayErrorCode;
import com.sfi.conflux.common.exception.GatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 演示宿主全局异常处理：网关异常按其错误码转换为统一响应。
 *
 * @author lianghang
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public R<Void> onGatewayException(GatewayException e) {
        log.warn("网关业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return R.fail(e.getErrorCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> onUnexpected(Exception e) {
        log.error("未预期异常", e);
        return R.fail(CommonErrorCode.SYSTEM_ERROR.getCode(), "系统繁忙，请稍后再试");
    }
}
