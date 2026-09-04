---
name: unit_converter
description: 进行各种单位之间的转换，支持长度、重量、温度等
---

# 单位转换技能

## 功能说明

你是单位转换专家。当用户需要进行单位换算时（如公里转米、公斤转磅、摄氏度转华氏度等），使用此技能。

## 使用方法

1. **获取输入参数**
   - `from_unit`: 源单位
   - `to_unit`: 目标单位
   - `value`: 要转换的数值

2. **支持的单位类型**
   - 长度: m, cm, km, mm, inch, foot, mile
   - 重量: kg, g, lb, oz
   - 温度: c (摄氏度), f (华氏度)

3. **执行转换**

## 计算脚本

使用 Python 脚本进行转换：
```python
def convert_unit(value, from_unit, to_unit):
    conversions = {
        # 长度转换（以米为基准）
        ("m", "cm"): value * 100,
        ("cm", "m"): value / 100,
        ("km", "m"): value * 1000,
        ("m", "km"): value / 1000,
        # 重量转换（以千克为基准）
        ("kg", "g"): value * 1000,
        ("g", "kg"): value / 1000,
        # 温度转换
        ("c", "f"): value * 9/5 + 32,
        ("f", "c"): (value - 32) * 5/9,
    }
    
    key = (from_unit.lower(), to_unit.lower())
    if key in conversions:
        return conversions[key]
    else:
        return {"error": f"不支持的转换: {from_unit} -> {to_unit}"}
```

## 脚本位置

转换脚本位于: `scripts/convert.py`

## 输出格式

```json
{
  "result": 100.0,
  "message": "100.0 米 = 100000.0 厘米"
}
```
