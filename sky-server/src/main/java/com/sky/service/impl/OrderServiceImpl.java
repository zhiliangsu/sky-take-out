package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 1. 异常情况的处理(收货地址为空, 超出配送范围, 购物车为空)
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        // 验证收货地址是否为空
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 验证收货地址是否超出配送范围
        checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        // 验证购物车是否为空
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 2. 向订单表插入1条记录
        // 构造订单对象
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, order);
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setUserId(userId);
        order.setOrderTime(LocalDateTime.now());
        order.setPayStatus(Orders.UN_PAID);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());

        orderMapper.insert(order);

        // 3. 向订单明细表插入n条记录
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        // 3. 清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        // 4. 封装OrderSubmitVO对象并返回
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        /* // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        // 调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), // 商户订单号
                new BigDecimal(0.01), // 支付金额，单位 元
                "苍穹外卖订单", // 商品描述
                user.getOpenid() // 微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo; */

        // 因为开通的小程序不是商家版, 没有支付功能, 为方便测试这里直接调用支付成功接口修改订单状态
        paySuccess(ordersPaymentDTO.getOrderNumber());
        return OrderPaymentVO.builder()
                .timeStamp(String.valueOf(LocalDateTime.of(2023, 1, 1, 0, 0, 0)))
                .build();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders orderDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders order = Orders.builder()
                .id(orderDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(order);

        // 实现来单提醒功能
        Map map = new HashMap();
        map.put("type", 1); // 消息类型: 1代表来单提醒 2代表催单提醒
        map.put("orderId", orderDB.getId()); // 订单id
        map.put("content", "订单号: " + outTradeNo); // 提示消息: 订单号
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 历史订单分页查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    @Transactional
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        // 设置userId
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        // 分页条件查询, 得到满足条件的订单列表
        Page<Orders> ordersList = orderMapper.pageQuery(ordersPageQueryDTO);

        // 封装OrderVO进行响应
        List<OrderVO> orderVOList = new ArrayList<>();
        if (ordersList != null && ordersList.getTotal() > 0) {
            // 遍历page对象, 获取订单id, 查询订单明细
            for (Orders order : ordersList) {
                // 复制共同字段
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                // 根据orderId获取每张订单的菜品列表
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(order.getId());
                orderVO.setOrderDetailList(orderDetails);
                // 添加到orderVOList中
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(ordersList.getTotal(), orderVOList);
    }

    /**
     * 根据id查询订单详情
     *
     * @param id
     * @return
     */
    @Override
    @Transactional
    public OrderVO queryOrderDetail(Long id) {
        // 1.根据id查询订单
        Orders order = orderMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 2.根据订单id查询订单明细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        // 3.封装OrderVO并返回响应结果
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        orderVO.setOrderDetailList(orderDetails);
        return orderVO;
    }

    /**
     * 根据id取消订单
     *
     * @param id
     * @return
     */
    @Override
    public void cancelOrder(Long id) throws Exception {
        // 根据id查询订单
        Orders order = orderMapper.getById(id);
        Integer status = order.getStatus();
        // 校验订单是否存在
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (status > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 订单处于待接单状态下取消, 需要进行退款
        if (status == Orders.TO_BE_CONFIRMED) {
            // 调用微信支付退款接口
            /* weChatPayUtil.refund(
                    order.getNumber(),  // 商户订单号
                    order.getNumber(),  // 商户退款单号
                    new BigDecimal(0.01),   // 退款金额，单位 元
                    new BigDecimal(0.01));  // 原订单金额 */

            // 修改订单支付状态为退款
            order.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态, 取消原因, 取消时间
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason("用户取消");
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 再来一单
     *
     * @param id
     * @return
     */
    @Override
    public void repetition(Long id) {

        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询当前订单详情, 并把其中的所有菜品添加到购物车中
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);
        /* // 方式一:
        for (OrderDetail orderDetail : orderDetails) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        } */

        // 方式二: 参考答案
        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetails.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 订单条件查询
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        // 分页条件查询, 得到满足条件的订单列表
        Page<Orders> ordersList = orderMapper.pageQuery(ordersPageQueryDTO);

        // 封装OrderVO进行响应
        List<OrderVO> orderVOList = new ArrayList<>();
        if (ordersList != null && ordersList.getTotal() > 0) {
            // 遍历page对象, 获取订单id, 查询订单明细
            for (Orders order : ordersList) {
                // 复制共同字段
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);

                // 根据orderId获取每张订单里的每个菜品或套餐信息, 拼接成字符串
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(order.getId());
                StringBuilder orderDishes = new StringBuilder();
                orderDetails.forEach(orderDetail -> orderDishes.append(orderDetail.getName()).append("*")
                        .append(orderDetail.getNumber()).append(";"));
                orderVO.setOrderDishes(orderDishes.toString());

                // 添加到orderVOList中
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(ordersList.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 根据状态, 分别查询出待接单, 已接单(待派送), 派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 封装OrderStatisticsVO并返回
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     * @return
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        // 修改对应订单的状态为已确认
        Orders order = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(order);
    }

    /**
     * 拒单
     *
     * @param ordersRejectionDTO
     * @return
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 根据id查询订单
        Orders orderDB = orderMapper.getById(ordersRejectionDTO.getId());
        // 订单只有存在且状态为2(待接单)才可以拒单
        if (orderDB == null || !orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 确认订单支付状态,如果用户已支付,需要退款
        Integer payStatus = orderDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            // 调用微信支付退款接口
            /* String refund = weChatPayUtil.refund(
                    orderDB.getNumber(),  // 商户订单号
                    orderDB.getNumber(),  // 商户退款单号
                    new BigDecimal(0.01),   // 退款金额，单位 元
                    new BigDecimal(0.01));// 原订单金额
            log.info("申请退款: {}", refund); */
        }
        // 退款成功后, 根据订单id修改订单支付状态, 订单状态, 拒单原因, 取消时间
        Orders order = new Orders();
        order.setId(orderDB.getId());
        order.setStatus(Orders.CANCELLED);
        order.setPayStatus(Orders.REFUND);
        order.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 取消订单
     *
     * @param ordersCancelDTO
     * @return
     * @throws Exception
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 根据id查询订单
        Orders orderDB = orderMapper.getById(ordersCancelDTO.getId());

        // 确认订单支付状态,如果用户已支付,需要退款
        Integer payStatus = orderDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            // 调用微信支付退款接口
            /* String refund = weChatPayUtil.refund(
                    orderDB.getNumber(),  // 商户订单号
                    orderDB.getNumber(),  // 商户退款单号
                    new BigDecimal(0.01),   // 退款金额，单位 元
                    new BigDecimal(0.01));// 原订单金额
            log.info("申请退款: {}", refund); */
        }
        // 退款成功后, 根据订单id修改订单支付状态, 订单状态, 取消订单原因, 取消时间
        Orders order = new Orders();
        order.setId(orderDB.getId());
        order.setStatus(Orders.CANCELLED);
        order.setPayStatus(Orders.REFUND);
        order.setCancelReason(ordersCancelDTO.getCancelReason());
        order.setCancelTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    /**
     * 派送订单
     *
     * @param id
     * @return
     */
    @Override
    public void delivery(Long id) {
        // 根据id查询订单
        Orders orderDB = orderMapper.getById(id);
        // 订单只有存在且状态为3(待派送)才可以派送
        if (orderDB == null || !orderDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态为派送中
        Orders order = new Orders();
        order.setId(orderDB.getId());
        order.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(order);
    }

    /**
     * 完成订单
     *
     * @param id
     * @return
     */
    @Override
    public void complete(Long id) {
        // 根据id查询订单
        Orders orderDB = orderMapper.getById(id);
        // 订单只有存在且状态为4(派送中)才可以完成订单
        if (orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态为已完成
        Orders order = new Orders();
        order.setId(orderDB.getId());
        order.setStatus(Orders.COMPLETED);
        order.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(order);
    }

    @Override
    public void reminder(Long id) {
        // 根据id查询订单
        Orders orderDB = orderMapper.getById(id);
        // 查询订单是否存在
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 实现用户催单功能
        Map map = new HashMap();
        map.put("type", 2); // 消息类型: 1代表来单提醒 2代表催单提醒
        map.put("orderId", orderDB.getId()); // 订单id
        map.put("content", "订单号: " + orderDB.getNumber()); // 提示消息: 订单号
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }

    /**
     * 检查客户的收货地址是否超出配送范围
     *
     * @param address
     */
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address", shopAddress);
        map.put("output", "json");
        map.put("ak", ak);

        // 获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("店铺地址解析失败");
        }

        // 数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        // 店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address", address);
        // 获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("收货地址解析失败");
        }

        // 数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        // 用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin", shopLngLat);
        map.put("destination", userLngLat);
        map.put("steps_info", "0");

        // 路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if (!jsonObject.getString("status").equals("0")) {
            throw new OrderBusinessException("配送路线规划失败");
        }

        // 数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if (distance > 5000) {
            // 配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }
    }
}