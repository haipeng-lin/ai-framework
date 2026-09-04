---
name: calculate_bmi
description: 根据身高体重计算BMI身体质量指数，评估健康状态
---

# BMI 计算技能

## 功能说明

你是 BMI 计算专家。当用户询问关于体重指数、身体质量指数或健康评估时，使用此技能。

## 使用方法

1. **获取输入参数**
   - `weight_kg`: 体重（千克）
   - `height_m`: 身高（米）

2. **执行计算**
   - BMI = 体重(kg) / 身高(m)²

3. **健康评估标准**
   | BMI 范围 | 健康状态 |
   |----------|----------|
   | < 18.5 | 偏瘦 |
   | 18.5 ~ 24 | 正常 |
   | 24 ~ 28 | 偏胖 |
   | >= 28 | 肥胖 |

## 计算脚本

使用 Python 脚本进行计算：
```python
def calculate_bmi(weight_kg: float, height_m: float) -> dict:
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
```

## 脚本位置

计算脚本位于: `scripts/calculate_bmi.py`

## 输出格式

```json
{
  "bmi": 22.86,
  "status": "正常",
  "message": "您的BMI为 22.86，属于【正常】范围。"
}
```
