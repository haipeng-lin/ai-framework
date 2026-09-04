package org.happyhai.springai.alibaba.comprehensive.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

public class LightCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    private String productCode;
    private String powerOnCommand;
    private String powerOffCommand;
    private String productName;

    public LightCommand() {
    }

    public LightCommand(String productCode, String productName, String powerOnCommand, String powerOffCommand) {
        this.productCode = productCode;
        this.productName = productName;
        this.powerOnCommand = powerOnCommand;
        this.powerOffCommand = powerOffCommand;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getPowerOnCommand() {
        return powerOnCommand;
    }

    public void setPowerOnCommand(String powerOnCommand) {
        this.powerOnCommand = powerOnCommand;
    }

    public String getPowerOffCommand() {
        return powerOffCommand;
    }

    public void setPowerOffCommand(String powerOffCommand) {
        this.powerOffCommand = powerOffCommand;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    @Override
    public String toString() {
        return "LightCommand{" +
                "productCode='" + productCode + '\'' +
                ", productName='" + productName + '\'' +
                ", powerOnCommand='" + powerOnCommand + '\'' +
                ", powerOffCommand='" + powerOffCommand + '\'' +
                '}';
    }
}
