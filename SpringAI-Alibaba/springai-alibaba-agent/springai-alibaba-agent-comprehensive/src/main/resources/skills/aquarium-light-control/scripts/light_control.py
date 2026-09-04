# 水族灯控制脚本

def get_light_command(product_code: str, action: str) -> dict:
    """
    根据产品码和操作获取控制命令
    
    Args:
        product_code: 产品码，如 0x0102A201
        action: 操作，on/off
    
    Returns:
        包含命令信息的字典
    """
    command_map = {
        "0x0102A201": {
            "name": "水族灯 Pro",
            "on": "C90102A2010A0602001D",
            "off": "C90102A2010A211D"
        },
        "0x0102A202": {
            "name": "水族灯 Mini", 
            "on": "C90102A2020A0602002D",
            "off": "C90102A2020A212D"
        },
        "0x0102A203": {
            "name": "全光谱水族灯",
            "on": "C90102A2030A0602033D",
            "off": "C90102A2030A213D"
        }
    }
    
    if product_code not in command_map:
        return {
            "error": f"不支持的产品码: {product_code}",
            "available": list(command_map.keys())
        }
    
    product = command_map[product_code]
    action_key = "on" if action.lower() in ["on", "开", "开灯"] else "off"
    
    return {
        "product_code": product_code,
        "product_name": product["name"],
        "action": action,
        "command": product[action_key],
        "mqtt_topic": f"device/control/{product_code}",
        "status": "ready_to_send"
    }


if __name__ == "__main__":
    result = get_light_command("0x0102A201", "on")
    print(result)
