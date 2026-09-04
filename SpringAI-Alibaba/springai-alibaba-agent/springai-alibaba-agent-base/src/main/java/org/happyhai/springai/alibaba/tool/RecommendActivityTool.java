package org.happyhai.springai.alibaba.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class RecommendActivityTool implements BiFunction<RecommendActivityRequest, ToolContext, String> {

    private static final Logger logger = LoggerFactory.getLogger(RecommendActivityTool.class);

    private static final Map<String, List<Map<String, String>>> CITY_ACTIVITIES = Map.of(
            "广州", List.of(
                    Map.of("name", "广州塔", "description", "广州地标性建筑，可俯瞰全城夜景", "address", "广州市海珠区阅江西路222号", "hours", "09:30-22:00"),
                    Map.of("name", "珠江夜游", "description", "沿珠江欣赏广州夜景", "address", "广州市越秀区沿江东路", "hours", "18:00-22:00"),
                    Map.of("name", "北京路步行街", "description", "历史与现代结合的商业步行街", "address", "广州市越秀区北京路", "hours", "全天"),
                    Map.of("name", "上下九步行街", "description", "老广州特色商业街", "address", "广州市荔湾区上下九路", "hours", "全天"),
                    Map.of("name", "白云山", "description", "南粤名山，适合登山健身", "address", "广州市白云区白云山南路", "hours", "06:00-22:00")
            ),
            "深圳", List.of(
                    Map.of("name", "世界之窗", "description", "荟萃世界著名景观的主题公园", "address", "深圳市南山区华侨城", "hours", "09:00-22:00"),
                    Map.of("name", "东部华侨城", "description", "集生态旅游、娱乐休闲为一体", "address", "深圳市盐田区大梅沙", "hours", "09:30-18:00"),
                    Map.of("name", "欢乐谷", "description", "大型主题乐园", "address", "深圳市南山区华侨城", "hours", "09:30-22:00"),
                    Map.of("name", "深圳湾公园", "description", "海滨休闲公园，可骑行散步", "address", "深圳市南山区深圳湾", "hours", "全天"),
                    Map.of("name", "大梅沙海滨公园", "description", "深圳知名海滨浴场", "address", "深圳市盐田区盐梅路", "hours", "06:00-23:00")
            ),
            "北京", List.of(
                    Map.of("name", "故宫", "description", "明清两代皇家宫殿", "address", "北京市东城区景山前街4号", "hours", "08:30-17:00"),
                    Map.of("name", "长城", "description", "中国古代伟大的防御工程", "address", "北京市延庆区G6京藏高速", "hours", "07:00-18:00"),
                    Map.of("name", "颐和园", "description", "清代皇家园林", "address", "北京市海淀区新建宫门路19号", "hours", "06:30-18:00"),
                    Map.of("name", "天坛", "description", "明清帝王祭祀天地之所", "address", "北京市东城区天坛内东里7号", "hours", "06:00-21:00"),
                    Map.of("name", "什刹海", "description", "老北京胡同文化区", "address", "北京市西城区地安门西大街", "hours", "全天")
            ),
            "上海", List.of(
                    Map.of("name", "外滩", "description", "万国建筑博览群", "address", "上海市黄浦区中山东一路", "hours", "全天"),
                    Map.of("name", "东方明珠", "description", "上海地标性建筑", "address", "上海市浦东新区世纪大道1号", "hours", "08:00-21:30"),
                    Map.of("name", "豫园", "description", "明代园林建筑", "address", "上海市黄浦区安仁街137号", "hours", "08:30-17:00"),
                    Map.of("name", "田子坊", "description", "石库门里弄文化创意区", "address", "上海市黄浦区泰康路210弄", "hours", "全天"),
                    Map.of("name", "上海迪士尼乐园", "description", "中国大陆第一个迪士尼乐园", "address", "上海市浦东新区川沙镇黄赵路310号", "hours", "08:30-20:30")
            ),
            "杭州", List.of(
                    Map.of("name", "西湖", "description", "世界文化遗产，风景如画", "address", "杭州市西湖区西湖风景名胜区", "hours", "全天"),
                    Map.of("name", "灵隐寺", "description", "千年古刹，香火鼎盛", "address", "杭州市西湖区灵隐路法云弄1号", "hours", "07:00-18:00"),
                    Map.of("name", "宋城", "description", "大型宋代文化主题公园", "address", "杭州市西湖区之江路148号", "hours", "09:00-22:00"),
                    Map.of("name", "西溪湿地", "description", "城市湿地公园，生态宝库", "address", "杭州市西湖区天目山路518号", "hours", "08:00-17:30"),
                    Map.of("name", "雷峰塔", "description", "西湖十景之一", "address", "杭州市西湖区南山路15号", "hours", "08:00-20:00")
            ),
            "成都", List.of(
                    Map.of("name", "宽窄巷子", "description", "清代胡同建筑群", "address", "成都市青羊区长顺上街127号", "hours", "全天"),
                    Map.of("name", "锦里古街", "description", "三国文化特色商业街", "address", "成都市武侯区武侯祠大街231号", "hours", "全天"),
                    Map.of("name", "大熊猫繁育研究基地", "description", "观赏大熊猫的最佳地点", "address", "成都市成华区熊猫大道1375号", "hours", "07:30-18:00"),
                    Map.of("name", "都江堰", "description", "古代水利工程奇迹", "address", "成都市都江堰市城西", "hours", "08:00-18:00"),
                    Map.of("name", "青城山", "description", "道教发源地之一", "address", "成都市都江堰市青城山镇", "hours", "08:00-18:00")
            ),
            "重庆", List.of(
                    Map.of("name", "洪崖洞", "description", "巴渝特色吊脚楼", "address", "重庆市渝中区嘉陵江滨江路", "hours", "全天"),
                    Map.of("name", "解放碑", "description", "重庆地标，抗战胜利纪念碑", "address", "重庆市渝中区民权路", "hours", "全天"),
                    Map.of("name", "长江索道", "description", "山城空中巴士", "address", "重庆市渝中区新华路151号", "hours", "07:30-22:00"),
                    Map.of("name", "磁器口古镇", "description", "千年古镇", "address", "重庆市沙坪坝区磁器口镇", "hours", "全天"),
                    Map.of("name", "武隆天生三桥", "description", "自然奇观", "address", "重庆市武隆区仙女山镇", "hours", "07:30-17:00")
            ),
            "西安", List.of(
                    Map.of("name", "秦始皇兵马俑", "description", "世界第八大奇迹", "address", "西安市临潼区秦陵北路", "hours", "08:30-18:00"),
                    Map.of("name", "大雁塔", "description", "唐代著名建筑", "address", "西安市雁塔区大雁塔南广场", "hours", "08:00-18:30"),
                    Map.of("name", "回民街", "description", "特色小吃一条街", "address", "西安市莲湖区北院门", "hours", "全天"),
                    Map.of("name", "城墙", "description", "中国现存最完整的古城墙", "address", "西安市碑林区东大街", "hours", "08:00-22:00"),
                    Map.of("name", "华清池", "description", "唐代皇家温泉", "address", "西安市临潼区华清路38号", "hours", "07:00-19:00")
            ),
            "南京", List.of(
                    Map.of("name", "中山陵", "description", "孙中山先生陵墓", "address", "南京市玄武区钟山风景名胜区", "hours", "08:30-17:00"),
                    Map.of("name", "夫子庙", "description", "秦淮河畔传统文化区", "address", "南京市秦淮区贡院街", "hours", "全天"),
                    Map.of("name", "明孝陵", "description", "朱元璋陵墓", "address", "南京市玄武区钟山风景名胜区", "hours", "06:30-18:30"),
                    Map.of("name", "总统府", "description", "近代历史建筑", "address", "南京市玄武区长江路292号", "hours", "08:30-18:00"),
                    Map.of("name", "玄武湖", "description", "江南最大的皇家园林湖泊", "address", "南京市玄武区玄武门路1号", "hours", "06:00-21:00")
            ),
            "武汉", List.of(
                    Map.of("name", "黄鹤楼", "description", "江南三大名楼之一", "address", "武汉市武昌区蛇山西山坡特1号", "hours", "08:00-18:00"),
                    Map.of("name", "东湖", "description", "中国最大的城中湖", "address", "武汉市武昌区东湖路特1号", "hours", "06:00-22:00"),
                    Map.of("name", "户部巷", "description", "武汉特色小吃街", "address", "武汉市武昌区户部巷", "hours", "全天"),
                    Map.of("name", "武汉大学", "description", "中国最美大学之一，樱花闻名", "address", "武汉市武昌区武汉大学", "hours", "全天"),
                    Map.of("name", "汉正街", "description", "汉口商业发源地", "address", "武汉市硚口区汉正街", "hours", "全天")
            )
    );

    @Override
    public String apply(RecommendActivityRequest request, ToolContext context) {
        String city = request.getCity();
        logger.info("🏙️ 推荐活动工具被调用！城市: {}", city);

        List<Map<String, String>> activities = CITY_ACTIVITIES.get(city);

        if (activities == null) {
            return "抱歉，暂不支持 " + city + " 的活动推荐。目前支持的城市有：" + String.join("、", CITY_ACTIVITIES.keySet());
        }

        StringBuilder result = new StringBuilder();
        result.append("📍 ").append(city).append(" 推荐活动：\n\n");

        for (int i = 0; i < activities.size(); i++) {
            Map<String, String> activity = activities.get(i);
            result.append(i + 1).append(". ").append(activity.get("name")).append("\n");
            result.append("   📝 ").append(activity.get("description")).append("\n");
            result.append("   📍 地址：").append(activity.get("address")).append("\n");
            result.append("   ⏰ 开放时间：").append(activity.get("hours")).append("\n\n");
        }

        return result.toString();
    }

    public static ToolCallback create() {
        return FunctionToolCallback.builder("recommend_activity", new RecommendActivityTool())
                .description("推荐城市热门活动和景点，适合旅游规划")
                .inputType(RecommendActivityRequest.class)
                .build();
    }
}
