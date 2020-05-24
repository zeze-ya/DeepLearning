package com.yc.practice.mall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yc.common.constant.CommonConstant;
import com.yc.common.constant.CommonEnum;
import com.yc.common.global.error.Error;
import com.yc.common.global.error.ErrorException;
import com.yc.core.mall.entity.MallGood;
import com.yc.core.mall.entity.MallOrder;
import com.yc.core.mall.entity.MallOrderItem;
import com.yc.core.mall.entity.MallShipping;
import com.yc.core.mall.mapper.MallOrderMapper;
import com.yc.core.mall.mapper.MallShippingMapper;
import com.yc.core.mall.model.form.OrderForm;
import com.yc.core.mall.model.query.OrderQuery;
import com.yc.practice.common.UserUtil;
import com.yc.practice.mall.service.MallGoodService;
import com.yc.practice.mall.service.MallOrderItemService;
import com.yc.practice.mall.service.MallOrderLogService;
import com.yc.practice.mall.service.MallOrderService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
* 功能描述：
*
*  <p>版权所有：</p>
*  未经本人许可，不得以任何方式复制或使用本程序任何部分
*
* @Company: 紫色年华
* @Author xieyc
* @Date 2020-04-08
* @Version: 1.0.0
*
*/
@Service
@Transactional(rollbackFor = Exception.class)
public class MallOrderServiceImpl extends ServiceImpl<MallOrderMapper, MallOrder> implements MallOrderService {

    private final MallOrderItemService mallOrderItemService;
    private final RedisTemplate redisTemplate;
    private final MallGoodService mallGoodService;
    private final MallOrderLogService mallOrderLogService;
    private final MallShippingMapper mallShippingMapper;

    @Autowired
    public MallOrderServiceImpl(MallOrderItemService mallOrderItemService,RedisTemplate redisTemplate,
                                MallShippingMapper mallShippingMapper,MallOrderLogService mallOrderLogService,
                                MallGoodService mallGoodService){
        this.mallOrderItemService = mallOrderItemService;
        this.redisTemplate = redisTemplate;
        this.mallOrderLogService = mallOrderLogService;
        this.mallGoodService = mallGoodService;
        this.mallShippingMapper = mallShippingMapper;
    }

    @Override
    public JSONObject createOrder(OrderForm orderForm) {
        // 收货地址校验
        MallShipping mallShipping = mallShippingMapper.selectById(orderForm.getMallShippingId());
        if(ObjectUtil.isNull(mallShipping)){
            throw new ErrorException(Error.ShippingNotFound);
        }
        // ========= 保存订单信息 ========
        MallOrder mallOrder = new MallOrder();
        BeanUtil.copyProperties(orderForm,mallOrder);
        mallOrder.setOrderNo(generateOrderNo());
        mallOrder.setCreateUserId(UserUtil.getUserId());
        mallOrder.setState(CommonEnum.OrderState.ORDER_STATE_10.getCode());
        // 处理收货地址
        mallOrder.setReceiverName(mallShipping.getReceiverName());
        mallOrder.setReceiverPhone(mallShipping.getReceiverPhone());
        mallOrder.setRegionCode(mallShipping.getRegionCode());
        mallOrder.setReceiverProvince(mallShipping.getReceiverProvince());
        mallOrder.setReceiverCity(mallShipping.getReceiverCity());
        mallOrder.setReceiverArea(mallShipping.getReceiverArea());
        mallOrder.setReceiverAddress(mallShipping.getReceiverAddress());
        this.baseMapper.insert(mallOrder);
        // ========= 保存订单商品信息 ========
        List<MallOrderItem> orderItemList = orderForm.getGoodsInfo();
        List<MallGood> goodList = new ArrayList<>();
        BigDecimal amount = BigDecimal.valueOf(0);
        for (MallOrderItem curr : orderItemList) {
            if(StringUtils.isBlank(curr.getGoodId())){
                throw new ErrorException(Error.GoodNotFound);
            }
            MallGood mallGood = this.mallGoodService.getBaseMapper().selectById(curr.getGoodId());
            if(mallGood.getStock()<curr.getGoodNum()){
                throw new ErrorException(Error.StockLow);
            }
            curr.setMallOrderId(mallOrder.getMallOrderId());
            curr.setOrderNo(mallOrder.getOrderNo());
            curr.setSysUserId(UserUtil.getUserId());
            amount = amount.add(mallGood.getPrice().multiply(BigDecimal.valueOf(curr.getGoodNum())));
            mallGood.setSale(curr.getGoodNum()+mallGood.getSale());
            mallGood.setStock(mallGood.getStock()-curr.getGoodNum());
            goodList.add(mallGood);
        }
        // 校验总价
        if(amount.compareTo(orderForm.getPayAmount()) != 0){
            throw new ErrorException(Error.AmountError);
        }
        this.mallOrderItemService.saveBatch(orderItemList);
        // 减库存 加销量
        this.mallGoodService.updateBatchById(goodList);
        // 订单变更记录
        mallOrderLogService.saveOrderLog(mallOrder.getMallOrderId(),CommonEnum.OrderLogState.WAIT_PAY.getCode(),"正常订单");
        JSONObject jsonObject = new JSONObject();
        String shipping =
                mallShipping.getReceiverName()+" "+mallShipping.getReceiverPhone()+ " " +
                        mallShipping.getReceiverProvince()+" "+mallShipping.getReceiverCity()+" "+
                        mallShipping.getReceiverArea()+" "+mallShipping.getReceiverAddress();
        jsonObject.put("shipping",shipping);
        jsonObject.put("payAmount",mallOrder.getPayAmount());
        return jsonObject;
    }

    @Override
    public Page<MallOrder> orderPage(Page<MallOrder> page, OrderQuery query) {
        return this.baseMapper.page(page,query);
    }

    @Override
    public void cancelOrder(String mallOrderId) {
        MallOrder mallOrder = new MallOrder();
        mallOrder.setState(CommonEnum.OrderState.ORDER_STATE_0.getCode());
        boolean flag = this.update(mallOrder,new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getMallOrderId,mallOrderId)
        );
        if(!flag){
            throw new ErrorException(Error.paramError);
        }
        // 订单变更记录
        mallOrderLogService.saveOrderLog(mallOrderId,CommonEnum.OrderLogState.INVALID.getCode(),"订单取消");
    }


    // ======================== 私有方法 ========================
    /**
     * 生成唯一订单号
     * @return 23位订单号[当前时间(毫秒) + 自增id]
     */
    private String generateOrderNo(){
        StringBuilder sb = new StringBuilder();
        Long nowLong = Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()));
        sb.append(nowLong.toString());
        String date = new SimpleDateFormat("yyyyMMddHHmm").format(new Date());
        String key = CommonConstant.ORDER_NO_TODAY_CACHE + date;
        Long increment = redisTemplate.opsForValue().increment(key,1);
        String incrementStr = increment.toString();
        if (incrementStr.length() <= 6) {
            sb.append(String.format("%06d", increment));
        } else {
            sb.append(incrementStr);
        }
        return sb.toString();
    }
}
