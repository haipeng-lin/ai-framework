"""
单位转换脚本
用法: python convert.py <value> <from_unit> <to_unit>
示例: python convert.py 100 m cm
"""
import json
import sys


def convert_unit(value: float, from_unit: str, to_unit: str) -> dict:
    """单位转换"""
    from_u = from_unit.lower()
    to_u = to_unit.lower()
    
    # 长度转换（以米为基准）
    length_to_m = {"m": 1, "cm": 0.01, "km": 1000, "mm": 0.001, 
                   "inch": 0.0254, "foot": 0.3048, "mile": 1609.344}
    # 重量转换（以千克为基准）
    weight_to_kg = {"kg": 1, "g": 0.001, "lb": 0.453592, "oz": 0.0283495}
    
    result = None
    message = ""
    
    # 长度转换
    if from_u in length_to_m and to_u in length_to_m:
        meters = value * length_to_m[from_u]
        result = meters / length_to_m[to_u]
        message = f"{value} {from_unit} = {round(result, 6)} {to_unit}"
    # 重量转换
    elif from_u in weight_to_kg and to_u in weight_to_kg:
        kg = value * weight_to_kg[from_u]
        result = kg / weight_to_kg[to_u]
        message = f"{value} {from_unit} = {round(result, 6)} {to_unit}"
    # 温度转换
    elif from_u == "c" and to_u == "f":
        result = value * 9/5 + 32
        message = f"{value}°C = {round(result, 2)}°F"
    elif from_u == "f" and to_u == "c":
        result = (value - 32) * 5/9
        message = f"{value}°F = {round(result, 2)}°C"
    else:
        return {"error": f"不支持的转换: {from_unit} -> {to_unit}"}
    
    return {"result": round(result, 6), "message": message}


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print(json.dumps({"error": "请提供 数值 源单位 目标单位，例如: python convert.py 100 m cm"}))
        sys.exit(1)
    
    try:
        value = float(sys.argv[1])
        from_unit = sys.argv[2]
        to_unit = sys.argv[3]
        result = convert_unit(value, from_unit, to_unit)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except ValueError as e:
        print(json.dumps({"error": f"参数错误: {e}"}))
