// 文件名: WeatherRequest.java
package org.happyhai.springai.alibaba.tool;

public class WeatherRequest {
    private String city;

    // 必须提供 getter 和 setter 方法，以便框架能够进行 JSON 序列化和反序列化
    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}