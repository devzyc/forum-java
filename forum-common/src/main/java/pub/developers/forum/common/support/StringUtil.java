package pub.developers.forum.common.support;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.ValueFilter;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author Qiangqiang.Bian
 * @create 20/7/23
 * @desc
 **/
public class StringUtil {
    private StringUtil() {
    }

    private static final String MD5_PRE_KEY = "1357924680QWERqwer";
    private static final String MD5_POST_KEY = "0987654321zxcv";
    private static final String[] COLOR_CODE = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F" };

    public static void main(String[] args) {
        System.out.println(getColor(MD5_POST_KEY));
    }

    /**
     * 获取16进制颜色值
     * @param str
     * @return
     */
    public static String getColor(String str) {
        int hash = str.hashCode();
        Integer[] rgb = new Integer[]{(hash & 0xFF0000) >> 16, (hash & 0x00FF00) >> 8, hash & 0x0000FF};

        StringBuilder hexCode = new StringBuilder("#");
        for (int i = 0; i < rgb.length; i ++) {
            int rgbItem = rgb[i];

            if (rgbItem < 0) {
                rgbItem = 0;
            } else if (rgbItem > 255){
                rgbItem = 255;
            }

            hexCode.append(COLOR_CODE[rgbItem / 16]).append(COLOR_CODE[rgbItem % 16]);
        }

        return hexCode.toString();
    }

    /**
     * 生成 uuid 字符串
     *
     * @return
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 对 value 进行 md5 加密
     * @param password
     * @return
     */
    public static String md5UserPassword(String password) {
        return md5(MD5_PRE_KEY + password + MD5_POST_KEY);
    }

    /**
     * 对 value 进行 md5 加密
     * @param value
     * @return
     */
    public static String md5(String value) {
        return DigestUtils.md5Hex(value);
    }

    /**
     * json string 属性值最大长度
     */
    private static final Integer MAX_LENGTH_FOR_PER_PROPERTY = 100;

    /**
     * 序列化日志/审计内容时这些字段名下的值一律掩码，避免明文密码写进日志文件和 forum_opt_log 表
     */
    private static final Set<String> MASK_VALUE_FIELDS = new HashSet<>(Arrays.asList("password", "oldPassword", "newPassword"));

    public static String toJSONString(Object result) {
        return JSONObject.toJSONString(result
                , new ValueFilter() {
                    @Override
                    public Object process(Object object, String name, Object value) {
                        if (MASK_VALUE_FIELDS.contains(name)) {
                            return "***";
                        }
                        if(value instanceof String && ((String) value).length() > MAX_LENGTH_FOR_PER_PROPERTY){//String只打印前500个字符
                            return ((String) value).substring(0, MAX_LENGTH_FOR_PER_PROPERTY) + "...";
                        }
                        if(value instanceof byte[]){
                            String s = new String((byte[]) value, Charset.defaultCharset());
                            if(s.length() > MAX_LENGTH_FOR_PER_PROPERTY){
                                return s.substring(0, MAX_LENGTH_FOR_PER_PROPERTY) + "...";
                            }
                        }
                        return value;
                    }
                }
                , SerializerFeature.IgnoreNonFieldGetter);
    }


}