package com.auth.center.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * SPA 验票请求 DTO.
 *
 * 替代原 {@code Map<String, String>} 参数，对 CAS ticket 和 service URL 做非空和长度约束。
 */
public class TicketValidateRequest {

    /** CAS Service Ticket ID */
    @NotBlank(message = "ticket 不能为空")
    @Size(max = 500, message = "ticket 长度不能超过 500")
    private String ticket;

    /** 请求方服务 URL（需与签发时一致） */
    @NotBlank(message = "service 不能为空")
    @Size(max = 500, message = "service 长度不能超过 500")
    private String service;

    /**
     * 获取 ticket.
     *
     * @return ticket ID
     */
    public String getTicket() {
        return ticket;
    }

    /**
     * 设置 ticket.
     *
     * @param ticket ticket ID
     */
    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    /**
     * 获取 service URL.
     *
     * @return service URL
     */
    public String getService() {
        return service;
    }

    /**
     * 设置 service URL.
     *
     * @param service service URL
     */
    public void setService(String service) {
        this.service = service;
    }
}
