package com.jx.blackface.paycenter.controllers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;

import com.alibaba.fastjson.JSON;
import com.jx.argo.ActionResult;
import com.jx.argo.BeatContext;
import com.jx.argo.Model;
import com.jx.argo.annotations.Path;
import com.jx.blackface.gaea.sell.entity.LvzPackageSellEntity;
import com.jx.blackface.gaea.sell.entity.LvzSellProductEntity;
import com.jx.blackface.gaea.usercenter.annotaion.CheckUserLogin;
import com.jx.blackface.gaea.usercenter.entity.BFAreasEntity;
import com.jx.blackface.gaea.usercenter.entity.OldOrderBFGEntity;
import com.jx.blackface.orderplug.buzs.OrderBuz;
import com.jx.blackface.orderplug.buzs.OrderBuzForHunter;
import com.jx.blackface.orderplug.buzs.PayBuz;
import com.jx.blackface.orderplug.frame.RSBLL;
import com.jx.blackface.orderplug.util.WorkDayUtil;
import com.jx.blackface.orderplug.vo.OrderBFVo;
import com.jx.blackface.paycenter.actionresult.ActionResultUtils;
import com.jx.blackface.paycenter.buzs.QueryBuz;
import com.jx.blackface.paycenter.frame.PSF;
import com.jx.blackface.paycenter.vo.OrderPayvo;
import com.jx.blackface.paycenter.vo.SingleOrderPayvo;
import com.jx.blackface.servicecoreclient.entity.OrderBFGEntity;
import com.jx.blackface.servicecoreclient.entity.OrderFlowBFGEntity;
import com.jx.blackface.servicecoreclient.entity.PayOrderBFGEntity;
import com.jx.service.preferential.plug.buz.PreferentialAccountBuz;
import com.jx.service.preferential.plug.utils.DoubleTools;
import com.jx.service.preferential.plug.vo.UnitVO;

import net.sf.json.JSONObject;


public class PayCommonController extends PayBaseController{


	@Path("/m/payresult/{payid:\\d+}")
	public ActionResult checkpayretmobile(long payid){
		//long uid = getLoginUserid();
		//if(uid > 0){
			PayOrderBFGEntity pof = null;
			try {
				pof = PSF.getPayOrderbfgService().getPayOrderByid(payid);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(pof != null){
				model().add("order", pof);
			}
		//}
		try {
			Calendar nowtime=Calendar.getInstance();
			nowtime.setTime(new Date());
			if(WorkDayUtil.isWeekday(nowtime) && nowtime.get(Calendar.HOUR_OF_DAY)>9 && nowtime.get(Calendar.HOUR_OF_DAY)<18){
				beat().getModel().add("message1", "15分钟内小微会与您联系，请注意接听。");
			}else{//下班时间
				beat().getModel().add("message1", "小微的工作时间是工作日9:00－18:00，工作时间小微将及时回复您，请您耐心等待 。");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return view("m/pay1");
	}
	@Path("/payresult/{payid:\\d+}")
	@CheckUserLogin
	public ActionResult checkpayret(long payid){
		String url = "pay-fail";
		long uid = getLoginUserid();
		PayOrderBFGEntity pfo = null;
		String orderurl = "";
		if(payid > 0){
			try {
				pfo = PSF.getPayOrderbfgService().getPayOrderByid(payid);
				
				orderurl = "http://mycenter.lvzheng.com/orderdetail/"+payid;
				beat().getModel().add("order", pfo);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else{
			
			return payview("");
		}
		if(null != pfo && pfo.getUserid() == uid && pfo.getPaystate() == 1){
			url = "pay-succ";
			List<OrderBFGEntity> list = null;
			try {
				list = PSF.getOrderBFGService().getOrderListBycondition("payid = "+payid+" and paystate = 1", 1, 1, "orderid");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(null != list && list.size() > 0){
				long oid = list.get(0).getOrderid();
				OrderFlowBFGEntity ofb = null;
				try {
					ofb = PSF.getOrderFlowBFGService().loadOrderflowbyorderid(oid);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(ofb != null){
					ofb.getProcessid();
					orderurl = "http://mycenter.lvzheng.com/mywf/company/reg/"+ofb.getProcessid();
				}
			}
		}
		if(pfo == null){
			OldOrderBFGEntity ofg = null;
			try {
				ofg = RSBLL.rb.getOldOrderBFGService().loadOrderbyid(payid);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(null != ofg){
				if(ofg.getPaystate() == 2){
					url = "pay-succ";
				}else{
					orderurl = "http://wwww.lvzheng.com/paylist/"+payid;
				}
			}
		}
		
		beat().getModel().add("orderurl", orderurl);

		try {
			Calendar nowtime=Calendar.getInstance();
			nowtime.setTime(new Date());
			if(WorkDayUtil.isWeekday(nowtime) && nowtime.get(Calendar.HOUR_OF_DAY)>9 && nowtime.get(Calendar.HOUR_OF_DAY)<18){
				beat().getModel().add("message", "支付成功，小微即将为您服务");
				beat().getModel().add("message1", "15分钟内小微会与您联系，请注意接听。");
			}else{//下班时间
				beat().getModel().add("message", "恭喜您，支付成功！");
				beat().getModel().add("message1", "小微的工作时间是工作日9:00－18:00，工作时间小微将及时回复您，请您耐心等待 。");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return payview(url);
	}
	//支付方式改为侧栏
	@Path("/reqpayGetData/{pid:\\d+}")
	@CheckUserLogin
	public ActionResult reqpayGetData(long pid){
		System.out.println("test repay");
		Map<String, Object> resultmap=new HashMap<String, Object>();
		long uid = getLoginUserid();
		PayOrderBFGEntity pfo = null;
		List<SingleOrderPayvo> olist = null;
		if(pid > 0){
			try {
				pfo = PSF.getPayOrderbfgService().getPayOrderByid(pid);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if(pfo != null){//新订单
			if(pfo != null && uid > 0 && pfo.getPaystate() == 1){//支付过的
				resultmap.put("payState", "1");
				return ActionResultUtils.renderJson(JSON.toJSONString(resultmap));
			}
			resultmap.put("payState", "0");
			if(pfo != null && uid > 0 && pfo.getUserid() == uid){ 
				try {
					olist = QueryBuz.qb.queryOrderBypayid(pid);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if(olist != null && olist.size() > 0){
				resultmap.put("orderlist", olist);
				resultmap.put("payorder", pfo);
			}
		}
		return ActionResultUtils.renderJson(JSON.toJSONString(resultmap));
	}
	@Path("/mreqpay/{payid:\\d+}")
	public ActionResult mreqpay(long payid){
		Model model = beat().getModel();
		PayOrderBFGEntity payorder = null;
		try {
			payorder = PSF.getPayOrderbfgService().getPayOrderByid(payid);//RSBLL.getstance().getPayOrderService().getPayOrderByid(Long.valueOf(payid));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		model.add("payorder", payorder);
		List<OrderBFGEntity> orderlist = null;
		String condition = "payid="+payid;
		try {
			orderlist = PSF.getOrderBFGService().getOrderListBycondition(condition, 1, 99, "orderid");//RSBLL.getstance().getOrderBFGService().getOrderListBycondition("payid="+payid, 1, 99, "orderid");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(null != orderlist && orderlist.size() > 0){
			List<OrderPayvo> plist = new ArrayList<OrderPayvo>();
			for(OrderBFGEntity order : orderlist){
				OrderPayvo pv = new OrderPayvo();
				try {
					BFAreasEntity bfr = PSF.getAreaService().getAeasEntityById(order.getLocalid());
							//RSBLL.getstance().getAreaService().getAeasEntityById(order.getLocalid());
					if(bfr != null){
						pv.setLocalstr(bfr.getName());
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LvzSellProductEntity sell = null;
				try {
					sell = PSF.getSellProductService().getSellProductEntityById(order.getSellerid());
					//RSBLL.getstance().getLvzSellProductService().getSellProductEntityById(order.getSellerid());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(sell != null){
					pv.setProname(sell.getSell_product_name());
					//pv.setServername(sell.getSell_product_name());
				}
				pv.setOrderid(order.getPayid());//.setPayid(payorder.getPayid());
				pv.setLocalid(order.getLocalid());
				pv.setOrderid(order.getOrderid());
				pv.setPrice(order.getPaycount());
				plist.add(pv);
			}
			if(plist != null && plist.size() > 0){
				model.add("orderlist", plist);
			}
		}
		//Long ppid = Long.parseLong(payid);
		PayBuz.pb.dealWexixinpay(payid, beat());
		//PayCommon.pbc.dealWexixinpay(payid, beat());
		return payview("m/goToZhiFu");
	}
	@Path("/reqpay/{pid:\\d+}")
	@CheckUserLogin
	public ActionResult reqpay(long pid){
		long uid = getLoginUserid();
		PayOrderBFGEntity pfo = null;
		String returl = beat().getRequest().getParameter("returnurl");
		if(null != returl && !"".equals(returl)){
			beat().getModel().add("returnurl", returl);
		}
		List<OrderPayvo> olist = null;
		if(pid > 0){
			try {
				pfo = PSF.getPayOrderbfgService().getPayOrderByid(pid);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if(pfo == null){//老订单
			
		}else{
			if(pfo != null && uid > 0 && pfo.getPaystate() == 1){
				return payredirect("/payresult/"+pid);
			}
			if(pfo != null && uid > 0 && pfo.getUserid() == uid){
				double paydiscount = 0;
				try {
					olist = QueryBuz.qb.queryOrderlistBypayid(pid);
					Set<Long> Set_packageSellE = new HashSet<Long>(); 
					Iterator<OrderPayvo> iterator = olist.iterator();
					while(iterator.hasNext()){
						OrderPayvo order = iterator.next();
						if(order.getPackagesellid() == 0){
							continue;
						}
						iterator.remove();  //排除掉包含了商品包的订单信息
						Set_packageSellE.add(order.getPackagesellid());
					}
					
					//如果存在商品包
					if(!Set_packageSellE.isEmpty()){
						List<Map<String,Object>> Set_packageSellEList = new ArrayList<Map<String,Object>>();
						Map<String,Object> packageSellList = new HashMap<String,Object>();
						for(Long packageSellid : Set_packageSellE){
							try {
								LvzPackageSellEntity lvzPackageSellEntity = RSBLL.rb.getPackageSellService().getLvzPackageSellEntity(packageSellid);
								Map<String,Object> temp_packageSell = BeanUtils.describe(lvzPackageSellEntity);
								temp_packageSell.put(String.valueOf(packageSellid), getPackageSellEntityMap(lvzPackageSellEntity));
								Set_packageSellEList.add(temp_packageSell);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						if(!Set_packageSellEList.isEmpty()){
							beat().getModel().add("Set_packageSellEList", Set_packageSellEList);
						}
					}

					
					for(OrderPayvo order : olist){
						String coupon_id = order.getUsercoupon();
						if (StringUtils.isBlank(coupon_id))
							continue;
						UnitVO vo = PreferentialAccountBuz.getInstance().getAccountUnitVO(Long.parseLong(coupon_id));
						if (vo == null)
							continue;
						paydiscount = DoubleTools.add(paydiscount, vo.getRealQuota(order.getPrice()));
						paydiscount = order.getPrice() < paydiscount ? order.getPrice() : paydiscount;
						order.setUsercoupon(vo.getTitleNameString());
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				beat().getModel().add("paydiscount",paydiscount);
				beat().getModel().add("payorder", pfo);
			}
			if(olist != null && olist.size() > 0){
				beat().getModel().add("orderlist", olist);
			}
		}
		return payview("orderconfirm");
	}
	@Path("/checkpaystate/{payid:\\d+}")
	public ActionResult checkpaystate(final long payid){
		return new ActionResult(){

			@Override
			public void render(BeatContext beatContext) {
				// TODO Auto-generated method stub
				beatContext.getResponse().setCharacterEncoding("utf-8");
				beatContext.getResponse().setContentType("text/plain");
				
				int pstate = paystate(payid);
				
				JSONObject jo = new JSONObject();
				
				jo.put("paystate", pstate);
				
				String ret = "callback("+jo.toString()+")";
				
				try {
					beat().getResponse().getWriter().print(ret);
					beat().getResponse().getWriter().close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		};
	}
	@Path("/checkpay/{pid:\\d+}")
	@CheckUserLogin
	public ActionResult checkpay(final long pid){
		if(pid <= 0)
			return null;
		
		return new ActionResult(){

			@Override
			public void render(BeatContext beatContext) {
				// TODO Auto-generated method stub
				
				HttpServletRequest request = beat().getRequest();
				beatContext.getResponse().setCharacterEncoding("utf-8");
				beatContext.getResponse().setContentType("text/plain");
				try {
					request.setCharacterEncoding("UTF-8");
				} catch (UnsupportedEncodingException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
				PayOrderBFGEntity pfg = null;
				try {
					pfg = OrderBuz.ob.getPayorderByid(pid);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				JSONObject jo = new JSONObject();
				if(null != pfg && pfg.getPaystate() == 1){
					jo.put("ret", "ok");
				}else{
					jo.put("ret", "fail");
				}
				
				try {
					beat().getResponse().getWriter().print(jo.toString());
					beat().getResponse().getWriter().close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}};
	}
	
	public int paystate(long payid){
		PayOrderBFGEntity pfg = null;
		try {
			pfg = OrderBuz.ob.getPayorderByid(payid);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(null != pfg){
			return pfg.getPaystate();
		}else{
			return 0;
		}
	}
	@Path("/sigleorder/{payorderid:\\d+}")
	public ActionResult wxpay(long payorderid){
		PayOrderBFGEntity pfg = null;
		if(payorderid > 0){
			try {
				pfg = OrderBuz.ob.getPayorderByid(payorderid);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		OrderBFVo bf = null;
		if(pfg != null && pfg.getPaystate() == 0){
			try {
				List<OrderBFVo> olist = OrderBuz.ob.getOrderBFGbypayorder(payorderid);
				if(olist != null && olist.size() > 0){
					bf = olist.get(0);
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		beat().getModel().add("ordervo", bf);
		return payview("");
	}

	
//	@Path("/deal/preferential")
//	public ActionResult dealPreferential(){
//		long uid = getLoginUserid();
//		//如果有优惠活动，优惠活动跟payorder绑定，第一个参数是payorder的优惠信息，之后是order的优惠信息
//		//此版本不包含payorder优惠活动计算
//		long zeroPayid = 0;
//		Map<String,String[]> preferentialInfo = beat().getRequest().getParameterMap();
//		try {
//			if(!preferentialInfo.isEmpty()){
//				String orderid = "";
//				for(Map.Entry<String, String[]> entry : preferentialInfo.entrySet()){
//					if(entry.getValue() != null){
//						if(StringUtils.isEmpty(orderid)){
//							orderid = entry.getKey();
//						}
//						if(!PreferentialBuz.getInstance().orderDiscountPrePay(uid, entry.getKey(), entry.getValue()[0])){
//							continue;
//						}
//					}
//				}
//				if(!StringUtils.isEmpty(orderid)){
//					//计算payorder
//					long afterCal = PreferentialBuz.getInstance().payOrderDiscountPrePay(orderid);
//					if(afterCal < 0){
//						return ActionResultUtils.renderText("{\"success\":\"false\"}");
//					}else if(afterCal > 0){
//						zeroPayid = afterCal;
//					}
//				}
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return ActionResultUtils.renderText("{\"success\":\"true\",\"pay\":\""+ zeroPayid +"\"}");
//	}
	@Path("/deal/zero")
	public ActionResult dealZeroAfterPreferential(){
		String payid = beat().getRequest().getParameter("pay");
		if(!StringUtils.isEmpty(payid)){
			OrderBuzForHunter.obfhunter.afterpayOperation(Long.parseLong(payid), 1);
			String url = "http://pay.lvzheng.com/reqpay/"+payid;
			return ActionResultUtils.renderText("{\"success\":\"true\",\"url\":\"" + url + "\"}");
		}
		return ActionResultUtils.renderText("{\"success\":\"false\"}");
	}
	
	/***
	 * 通过packageSellId返回处理过的商品对象
	 * @param packageSellId
	 * @return
	 */
	public List<Map<String,Object>> getPackageSellEntityMap(LvzPackageSellEntity lvzPackageSellEntity){
		try {
			if(null != lvzPackageSellEntity){
				List<Map<String,Object>> packageMapList = new ArrayList<Map<String,Object>>();
				String sellids = lvzPackageSellEntity.getSellids();
				if(StringUtils.isNotBlank(sellids)){
					String[] split_sellid = sellids.split(",");
					for(String sellid : split_sellid){
						LvzSellProductEntity sellProductEntityById = RSBLL.rb.getLvzSellProductService().getSellProductEntityById(Long.valueOf(sellid));
						Map<String,Object> sellProductMap = new HashMap<String, Object>();
						try {
							sellProductMap = BeanUtils.describe(sellProductEntityById);
							Map<String,Object> map = new HashMap<String, Object>();
							BFAreasEntity aeasEntity = RSBLL.rb.getAreaService().getAeasEntityById(sellProductEntityById.getArea_id());
							if(aeasEntity != null){
								map.put("aeasname", aeasEntity.getName());
								map.put("aeasid", aeasEntity.getAreaid());
								BFAreasEntity cityEntity = RSBLL.rb.getAreaService().getAeasEntityById(Long.valueOf(aeasEntity.getParentid()));
								map.put("cityname", cityEntity.getName());
								map.put("cityid", cityEntity.getAreaid());
							}
							sellProductMap.put("queryData", "true");
							sellProductMap.putAll(map);
							//向list中添加map
							packageMapList.add(sellProductMap);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
				if(!packageMapList.isEmpty()){
					return packageMapList;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Collections.emptyList();
	}
	
	public static void main(String[] args) {
		try {
//			List<PreferentialAccountEntity> couns = PSF.getPreferentialAccountService().getApplicable(38346088915969L,38256619533825L);
//			System.out.println(couns.size());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
