package com.homework2.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Embeddable
public class TicketMetadata {
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_source", nullable = false, length = 32)
    private TicketSource source;

    @NotBlank
    @Column(name = "metadata_browser", nullable = false, length = 255)
    private String browser;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_device_type", nullable = false, length = 16)
    private DeviceType deviceType;

    public TicketMetadata() {
    }

    public TicketMetadata(TicketSource source, String browser, DeviceType deviceType) {
        this.source = source;
        this.browser = browser;
        this.deviceType = deviceType;
    }

    public TicketSource getSource() {
        return source;
    }

    public void setSource(TicketSource source) {
        this.source = source;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }
}
