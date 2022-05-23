package com.cnsesan.lfx.mtcp;

import com.alibaba.fastjson2.JSON;
import com.ybs.util.model.BaseAttr;
import com.ybs.util.request.Sm2RequestUtil;
import com.ybs.util.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * node传入参数，java利用对方提供jar包将数据post出去，并返回String型返回值
 */
public class DataUpload {

    private static Logger logger = LoggerFactory.getLogger(DataUpload.class);

    /**
     * 指定Date类型的格式（云南省妇幼健康信息平台要求格式）
     */
    private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 上传二维数组格式数据
     *
     * @param className   对象类名
     * @param serviceName 提交数据的服务名
     * @param dataArray   数据内容（二维数组）
     * @return String型返回值，出错时返回"ERROR"前缀
     */
    public static String UploadStringList(String className, String serviceName, Object[] dataArray) {
        if (dataArray.length > 1000 || dataArray.length < 1)
            return "ERROR - 数据长度为1-1000";
        ApiResponse response = null;
        List array = new ArrayList(); // 最终传输内容为array形式
        try {
            // 数据类型都在com.ybs.util.model里面
            Class cls = Class.forName("com.ybs.util.model." + className); // 通过反射获取类
            Constructor[] constructors = cls.getDeclaredConstructors(); // 获取类的所有构造方法
            Object[] tmpData = (Object[]) dataArray[0];
            Constructor constructor = GETConstructor(constructors, tmpData.length); // 根据参数长度获取构造方法
            Class[] clss; // 制定参数数量构造方法的参数类型列表
            if (constructor == null) return "ERROR - 未找到与参数数量相匹配的接口";
            else clss = constructor.getParameterTypes(); // 获取构造方法的参数类型列表
            // 依次提取data
            for (int k = 0; k < dataArray.length; k++) {
                Object[] data;
                try {
                    data = (Object[]) dataArray[k];
                } catch (ClassCastException classCastException) { // 出错说明该数据不是二维数组
                    classCastException.printStackTrace();
                    return "ERROR - 数据第 " + (k + 1) + " 项不是数组，请检查";
                }
                // node-java中，Date类型不输入Object，因此改用List存储每个实例对象
                List<Object> params = new ArrayList<>();
                if (clss.length > 0 && clss.length == data.length) { // 参数数量大于0，且参数数量与上传参数数量相同
                    for (int i = 0; i < clss.length; i++) { // 遍历构造参数
                        if (data[i] == null) {
                            params.add(data[i]); // null值不转换
                            continue; // 继续下一个参数
                        }
                        // 根据构造参数类型转换上传的参数的类型
                        switch (clss[i].getName()) {
                            case "int": // 整数转换
                                params.add(Integer.valueOf(data[i].toString()));
                                break;
                            case "java.lang.Double": // 浮点数转换
                                params.add(Double.valueOf(data[i].toString()));
                                break;
                            case "java.util.Date": // 日期型转换
                                params.add(df.parse(data[i].toString()));
                                break;
                            default: // 其余类型转为String，null保持不变
                                params.add(data[i].toString()); // 针对Class要求为String，而输入值为int的情况
                        }
                    }
                    // BaseAttr是所有model的父类方法，包含seqNum属性，它的子类不包含该属性
                    BaseAttr obj = (BaseAttr) constructor.newInstance(params.toArray()); // 参数必须转为Array形式，然后转换为父类型
                    obj.setSeqNum(k + 1); // 给父类型设置seqNum，括号中的数字是这条数据在data里的顺序，从1开始
                    array.add(obj); // 将实体对象加入Array
                }
            }
            if (array.size() < 1)  // Array中没有数据
                return "ERROR - 无可传输内容"; // 提供"ERROR"前缀供node判断
            else {
                response = Sm2RequestUtil.post(serviceName, array); // 传输单个对象会报错，必须传Array，即使只有1条数据
                Map map = new HashMap<>();
                map.put("code", response.getCode());
                map.put("msg", response.getMsg());
                map.put("data", response.getData());
                return JSON.toJSONString(map); // 成功返回内容
            }
        } catch (Exception e) { // 数据传输过程出错
            e.printStackTrace();
            return "ERROR - 目标接口不存在，或参数数量错误 " + e.getLocalizedMessage();
        }
    }

    /**
     * 上传JSON字符串格式数据
     *
     * @param className   对象类名
     * @param serviceName 提交数据的服务名
     * @param data        数据内容（JSON字符串数组）
     * @return String型返回值，出错时返回"ERROR"前缀
     */
    public static String UploadJsonList(String className, String serviceName, Object[] data) {
        if (data.length > 1000 || data.length < 1)
            return "ERROR - 数据长度为1-1000";
        ApiResponse response = null;
        List array = new ArrayList(); // 最终传输内容为array形式
        try {
            // 数据类型都在com.ybs.util.model里面
            Class cls = Class.forName("com.ybs.util.model." + className); // 通过反射获取类
            for (int i = 0; i < data.length; i++) {
                BaseAttr obj = (BaseAttr) JSON.parseObject(JSON.toJSONString(data[i]), cls);
                obj.setSeqNum(i + 1);
                array.add(obj);
            }
            if (array.size() < 1) { // Array中没有数据
                return "ERROR - 无可传输内容"; // 提供"ERROR"前缀供node判断
            } else {
                response = Sm2RequestUtil.post(serviceName, array); // 传输单个对象会报错，必须传Array，即使只有1条数据
                Map map = new HashMap<>();
                map.put("code", response.getCode());
                map.put("msg", response.getMsg());
                map.put("data", response.getData());
                return JSON.toJSONString(map); // 成功返回内容
            }
        } catch (Exception e) { // 出错返回内容
            e.printStackTrace();
            return "ERROR - 目标接口不存在，或参数数量错误 " + e.getLocalizedMessage();
        }
    }

    /**
     * 获取构造参数，不再重复重复多次循环
     *
     * @return 构造函数
     */
    private static Constructor GETConstructor(Constructor[] constructors, int length) {
        for (Constructor constructor : constructors) { // 遍历构造方法
            Class[] cls = constructor.getParameterTypes(); // 获取构造方法的参数类型列表
            if (cls.length > 0 && cls.length == length) return constructor; // 参数数量相符
        }
        return null;
    }
}