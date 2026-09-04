"""
BMI 计算脚本
用法: python calculate_bmi.py <weight_kg> <height_m>
"""
import json
import sys


def calculate_bmi(weight_kg: float, height_m: float) -> dict:
    """计算BMI并返回健康状态"""
    if height_m <= 0 or weight_kg <= 0:
        return {"error": "身高和体重必须大于0"}
    
    bmi = weight_kg / (height_m ** 2)
    bmi = round(bmi, 2)
    
    if bmi < 18.5:
        status = "偏瘦"
    elif bmi < 24:
        status = "正常"
    elif bmi < 28:
        status = "偏胖"
    else:
        status = "肥胖"
    
    return {
        "bmi": bmi,
        "status": status,
        "message": f"您的BMI为 {bmi}，属于【{status}】范围。"
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(json.dumps({"error": "请提供体重(kg)和身高(m)，例如: python calculate_bmi.py 70 1.75"}))
        sys.exit(1)
    
    try:
        weight = float(sys.argv[1])
        height = float(sys.argv[2])
        result = calculate_bmi(weight, height)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except ValueError:
        print(json.dumps({"error": "参数必须是数字"}))
