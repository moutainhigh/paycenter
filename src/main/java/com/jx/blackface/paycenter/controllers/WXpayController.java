package com.jx.blackface.paycenter.controllers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.jdom.Element;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.jx.argo.ActionResult;
import com.jx.argo.BeatContext;
import com.jx.argo.annotations.Path;
import com.jx.blackface.gaea.usercenter.entity.OldOrderBFGEntity;
import com.jx.blackface.gaea.usercenter.entity.PayProcessBFGEntity;
import com.jx.blackface.gaea.usercenter.entity.PayRecordBFGEntity;
import com.jx.blackface.orderplug.buzs.OrderBuz;
import com.jx.blackface.orderplug.buzs.PayBuz;
import com.jx.blackface.orderplug.common.CommonTools;
import com.jx.blackface.orderplug.common.MessageHandler;
import com.jx.blackface.orderplug.common.PayContents;
import com.jx.blackface.orderplug.common.Sign;
import com.jx.blackface.orderplug.common.XmlParser;
import com.jx.blackface.orderplug.frame.RSBLL;
import com.jx.blackface.orderplug.util.WorkDayUtil;
import com.jx.blackface.orderplug.vo.OrderBFVo;
import com.jx.blackface.orderplug.vo.PayMessageVo;
import com.jx.blackface.paycenter.buzs.QueryBuz;
import com.jx.blackface.paycenter.frame.PSF;
import com.jx.blackface.paycenter.utools.CommonUtils;
import com.jx.blackface.paycenter.vo.OrderPayvo;
import com.jx.blackface.servicecoreclient.entity.PayOrderBFGEntity;
import com.jx.blackface.tools.blackTrack.entity.WebLogs;
import com.jx.service.messagecenter.entity.PayMessageEntity;
import com.jx.service.preferential.plug.buz.PreferentialAccountBuz;
import com.jx.service.preferential.plug.vo.UnitVO;

import net.sf.json.JSONObject;

@Path("/wxpay")
public class WXpayController extends PayBaseController {

	@Path("/mreqpay")
	public ActionResult reqpay(){
		String payid = beat().getRequest().getParameter("payid");
		System.out.println("test payid is "+payid);
		String openid = "oxizHs94Ws-zUPjPH64vr1Vtj5QI";//beat().getRequest().getParameter("opendid");
		
		long payorderid = 0;
		if(null != payid && !"".equals(payid)){
			payorderid = Long.parseLong(payid);
		}
		PayOrderBFGEntity peg = null;
		if(payorderid > 0){
			try {
				peg = RSBLL.rb.getPayOrderService().getPayOrderByid(payorderid);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//if(peg != null){
			String ret = "";
			try {
				ret = PayBuz.pb.startPayByrecorde(payorderid, 1, openid, 1, 99);//.wb.startPay(payorderid, (long)(peg.getPaycount()*100), openid,1);
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			StringBuffer url = beat().getRequest().getRequestURL();
			String queryString = beat().getRequest().getQueryString();
			if(!StringUtils.isEmpty(queryString))
					url.append("?" + queryString);
			String ticket = "";
			try {
				ticket = PayBuz.pb.getWeixinJSToken(PayContents.weixin_app_id, PayContents.weixin_app_secret_id);
				Map<String,Object> map = Sign.tranceTokentojst(ticket, url.toString());
				beat().getModel().addAll(map);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			if(!ret.equals("")){
				System.out.println("readpay res is ======="+ret);
				String tstamp = new Date().getTime()+"";
				PayMessageEntity pme = MessageHandler.newstance().trancePayResultToentity(ret);
				String signkey = "appId="+PayContents.weixin_app_id+"&nonceStr=ibuaiVcKdpRxkhJA&package=prepay_id="
				+pme.getPrepay_id()+"&signType=MD5&timeStamp="+tstamp+"&key=EKGt478kaAWygmU9AcfkK2vanc8ss8Xj";
				signkey = CommonTools.MD5(signkey).toUpperCase();
				JSONObject jj = new JSONObject();
				beat().getModel().add("signkey", signkey);
				beat().getModel().add("timeStamp", tstamp);
				beat().getModel().add("nonstr", "ibuaiVcKdpRxkhJA");
				beat().getModel().add("pmestr", ret);
				beat().getModel().add("pme", pme);
				beat().getModel().add("msg", jj.toString());
			}
		//}
		
		return view("wx/weixinpay");
	}
	@Path("/showqr/{pid:\\d+}")
	public ActionResult payprapay(long pid){
		WebLogs logs = WebLogs.getIntanse(WXpayController.class, "payprapay");
		long uid = CommonUtils.getLoginuserid(beat());
		PayOrderBFGEntity pfo = null;
		List<OrderPayvo> olist = null;
		String retpath = beat().getRequest().getParameter("path");
		if(null != retpath && !"".equals(retpath)){
			beat().getModel().add("reurl", retpath);
		}
		if(pid > 0){
			try {
				pfo = PSF.getPayOrderbfgService().getPayOrderByid(pid);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if(pfo != null && uid > 0 && pfo.getUserid() == uid){
			try {
				olist = QueryBuz.qb.queryOrderlistBypayid(pid);
				for(OrderPayvo order : olist){
					String coupon_id = order.getUsercoupon();
					UnitVO vo = PreferentialAccountBuz.getInstance().getAccountUnitVO(Long.parseLong(coupon_id));
					if (vo == null)
						continue;
					
					order.setUsercoupon(vo.getTitleNameString());
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				logs.printErrorLog(e);
			}
		}
		if(olist != null && olist.size() > 0){
			beat().getModel().add("orderlist", olist);
			beat().getModel().add("payorder", pfo);
		}
		return payview("wx/wxqrcode");
	}
	/**
     * 支付成功后更新订单状态
     * @param orderid 新系统中改为payrecordid
     * @return
     */
    @Path("/updatepay/{orderid:\\d+}")
    public ActionResult updatepay(long orderid){
    	WebLogs logs = WebLogs.getIntanse(WXpayController.class, "updatepay");
    	logs.putParam("payorder", orderid);
    	try {
			PayBuz.pb.updatePay(orderid,1);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			logs.printErrorLog(e);
		}
    	PayOrderBFGEntity order = null;
    	try {
			order = RSBLL.rb.getPayOrderService().getPayOrderByid(orderid);//.rb.getPayRecordBFGService().loadPayRecordByid(orderid);//.getSorderEntityByid(orderid);
		} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				logs.printErrorLog(e);
		}
    	if(null != order){
    		model().add("order", order);
    	}

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
    	logs.printInfoLog();
    	return view("wx/pay1");  //跳转上线的时候改过来
    }
    @Path("/uporderpay/{orderid:\\d+}/{payid:\\d+}")
    public ActionResult updateorderpay(long orderid,long payid){
    	beat().getModel().add("orderid", orderid);
    	WebLogs logs = WebLogs.getIntanse(WXpayController.class,"updateorderpay");
    	logs.putParam("orderid", orderid);
    	logs.putParam("payid", payid);
    	String ret = "";
    	try {
			ret = PayBuz.pb.wxcheckpay(payid);
			logs.putParam("wxpaycheckresult", ret);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			logs.printErrorLog(e1);
		}finally{
			logs.printInfoLog();
		}
    	Element el = null;
    	if(ret != null){
    		try {
    			el = XmlParser.newInstance().parseXmlToElement(ret);
    		} catch (Exception e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    	long paym = 0;
    	if(el != null){
    		String returncode = el.getChild("return_code") == null?null:el.getChild("return_code").getValue();
    		String result_code = el.getChild("result_code") == null?null:el.getChild("result_code").getValue();
    		String total_fee = el.getChild("total_fee") == null?null:el.getChild("total_fee").getValue();
    		
    		if(null != returncode && "SUCCESS".equalsIgnoreCase(returncode) && null != result_code && "SUCCESS".equalsIgnoreCase(result_code)){
    			paym = Long.parseLong(total_fee);
    		}
    		PayRecordBFGEntity order = null;
        	try {
    			order = RSBLL.rb.getPayRecordBFGService().loadPayRecordByid(payid);
    			
    		} catch (Exception e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    				logs.printErrorLog(e);
    		}
        	if(order != null && order.getTotalfee() == paym ){
            	
        		if(order.getSyschannel() == 99){//新系统
        			try {
    					PayBuz.pb.updatePay(order.getOrderid(),1);
    				} catch (Exception e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    				}
        		}else{
        			long ooid = order.getOrderid();
        			logs.putParam("serviceorder", ooid);
        			OldOrderBFGEntity ole = null;
        			try {
    					ole =  RSBLL.rb.getOldOrderBFGService().loadOrderbyid(ooid);
    				} catch (Exception e) {
    					// TODO Auto-generated catch block
    					e.printStackTrace();
    					logs.printErrorLog(e);
    				}
        			if(ole != null && ole.getPaystate() != 2){
        				ole.setPaystate(2);
        				if(ole.getSignstate() != 1){
        					ole.setSignstate(1);
        					ole.setSigntime(new Date().getTime());
        				}
        				ole.setUpdatetime(new Date().getTime());
        				ole.setPaymoneycount(ole.getPaymoneycount());
        				try {
    						RSBLL.rb.getOldOrderBFGService().updateOrder(ole);
    						//添加支付纪录
    						PayProcessBFGEntity ppe = new PayProcessBFGEntity();
    		        		ppe.setContents("微信支付!");
    		        		ppe.setOpempid(0);
    		        		ppe.setOptime(new Date().getTime());
    		        		ppe.setOptype(1);
    		        		ppe.setOrderid(orderid);
    		        		ppe.setPaychannel(4);
    		        		ppe.setPaytotal(ole.getPaymoneycount());
    		        		try {
    		    				RSBLL.rb.getPayProcessBFGService().addNewPayProcess(ppe);
    		    			} catch (Exception e) {
    		    				// TODO Auto-generated catch block
    		    				e.printStackTrace();
    		    				logs.printErrorLog(e);
    		    			}
    					} catch (Exception e) {
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    						logs.printErrorLog(e);
    					}
        			}
        		}
        		
        	}
//        	
    	}
    	logs.printInfoLog();
    	return payview("wx/buys");
    }
	@Path("/qrcode")
	public ActionResult makeWeixinQrcode(){
		return new ActionResult(){

			public void render(BeatContext beatContext) {
				// TODO Auto-generated method stub
				WebLogs logs = WebLogs.getIntanse(WXpayController.class,"makeWeixinQrcode");
				String orderid = beat().getRequest().getParameter("orderid");
				logs.putParam("qrpayorderid", orderid);
				long tempsign = new Date().getTime();
				String signkey = "appid="+PayContents.weixin_app_id+"&mch_id=1243594202&nonce_str=BBvdr5atZ9D7s08X&product_id="+orderid+"&time_stamp="+tempsign+"&key=EKGt478kaAWygmU9AcfkK2vanc8ss8Xj";
				logs.putParam("before_signkey", signkey);
				//System.out.println("befor md5 is "+signkey);
				signkey = CommonTools.MD5(signkey);
				logs.putParam("after_signkey", signkey);
				System.out.println("signkey is "+signkey);
				String url = "weixin://wxpay/bizpayurl?appid="+PayContents.weixin_app_id+"&mch_id=1243594202&nonce_str=BBvdr5atZ9D7s08X&product_id="+orderid+"&time_stamp="+tempsign
						+"&sign="+signkey;
				//System.out.println("final url is "+url);
				try {
					ServletOutputStream sop = beat().getResponse().getOutputStream();
					String text = url; 
			        int width = 300; 
			        int height = 300; 
			        //二维码的图片格式 
			        String format = "gif"; 
			        Hashtable hints = new Hashtable(); 
			        //内容所使用编码 
			        hints.put(EncodeHintType.CHARACTER_SET, "utf-8"); 
			        BitMatrix bitMatrix = new MultiFormatWriter().encode(text, 
			                BarcodeFormat.QR_CODE, width, height, hints); 
					MatrixToImageWriter.writeToStream(bitMatrix, format, sop);
					sop.close();
					beat().getResponse().flushBuffer();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					logs.printErrorLog(e);
				}
				logs.printInfoLog();
				
			}
			
		};
	}
	@Path("/readypay")
	public ActionResult orderReadypay(){
		WebLogs logs = WebLogs.getIntanse(WXpayController.class,"orderReadypay");
		
		String orderid = beat().getRequest().getParameter("orderid");
		logs.putParam("reqpayid", orderid);
		OrderBFVo vo = null;
		try {
			vo = OrderBuz.ob.getOrderVobyID(Long.parseLong(orderid));
		} catch (NumberFormatException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			logs.printErrorLog(e2);
		} catch (Exception e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
			logs.printErrorLog(e2);
		}
    	String oid = "";
		
		//---支付－－－－－
				if(vo.getOrderstate() != 2 && vo.getOrderstate() != 10){
					String orderTime = vo.getBooktime();
					beat().getModel().add("orderTime", orderTime);
					long proid = vo.getProductid();
					//==============得到客户的信息实体start===========================================
					/**
					 * 	
					 */
					String ret = "";
					try {
						ret = PayBuz.pb.startPayByrecorde(vo.getOrderid(), (Long.parseLong(vo.getPaycount())*100), oid,PayContents.pay_wx_js);
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
						logs.printErrorLog(e1);
					}
		    		logs.putParam("reqpayretstr", ret);
					StringBuffer url = beat().getRequest().getRequestURL();
		    		String queryString = beat().getRequest().getQueryString();
		    		if(!StringUtils.isEmpty(queryString))
		    				url.append("?" + queryString);
		    		String ticket = "";
		    		try {
		    			ticket = PayBuz.pb.getWeixinJSToken(PayContents.weixin_app_id, PayContents.weixin_app_secret_id);
		    			Map<String,Object> map = Sign.tranceTokentojst(ticket, url.toString());
		    			beat().getModel().addAll(map);
		    		} catch (Exception e) {
		    			// TODO Auto-generated catch block
		    			e.printStackTrace();
		    			logs.printErrorLog(e);
		    		}
		    		if(!ret.equals("")){
		    			//System.out.println("readpay res is ======="+ret);
		    			String tstamp = new Date().getTime()+"";
		    			PayMessageVo pme = PayBuz.pb.trancePayResultToentity(ret);
		    			String signkey = "appId="+PayContents.weixin_app_id+"&nonceStr=ibuaiVcKdpRxkhJA&package=prepay_id="
		    			+pme.getPrepay_id()+"&signType=MD5&timeStamp="+tstamp+"&key=EKGt478kaAWygmU9AcfkK2vanc8ss8Xj";
		    			logs.putParam("befor_sign", signkey);
		    			signkey = CommonTools.MD5(signkey).toUpperCase();
		    			logs.putParam("atfer_sign", signkey);
		    			JSONObject jj = new JSONObject();
		    			beat().getModel().add("signkey", signkey);
		    			beat().getModel().add("timeStamp", tstamp);
		    			beat().getModel().add("nonstr", "ibuaiVcKdpRxkhJA");
		    			beat().getModel().add("pmestr", ret);
		    			beat().getModel().add("pme", pme);
		    			beat().getModel().add("msg", jj.toString());
		    		}
		    		
				}
		    
		    	logs.printInfoLog();
		    	return payview("pay/payorder");
	}
	@Path("/wxnative")
	public ActionResult nativPayurl(){
		return new ActionResult(){

			public void render(BeatContext beatContext) {
				// TODO Auto-generated method stub
				WebLogs logs = WebLogs.getIntanse(WXpayController.class,"nativPayurl");
				HttpServletRequest request = beat().getRequest();
				beatContext.getResponse().setCharacterEncoding("utf-8");
				beatContext.getResponse().setContentType("text/plain");
				try {
					request.setCharacterEncoding("UTF-8");
				} catch (UnsupportedEncodingException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				String proid = "";
				String queryString = PayBuz.pb.tranceUserMessageTostring(beat().getRequest());
				logs.putParam("nativepayquerystr", queryString);
				Element el = null;
				String oid = "";
				if(null != queryString && !"".equals(queryString)){
					try {
						el = XmlParser.newInstance().parseXmlToElement(queryString);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				if(el != null){
					proid = el.getChild("product_id").getValue();
					oid = el.getChild("openid").getValue();
				}
				String ret = "";
				if(null != proid && !"".equals(proid)){
					long orderid = Long.parseLong(proid);
					PayOrderBFGEntity vo = null;
					try {
						vo = PSF.getPayOrderbfgService().getPayOrderByid(orderid);
					} catch(Exception e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
						logs.printInfoLog();
					}
					boolean f = false;
					long pcount = 0l;
					int syschanel = 0;//默认旧数据
					if(vo != null){//新系统
						logs.putParam("systype", "platformsys");
						try {
							if(vo.getPaystate() != 1){
								pcount = (long)(vo.getPaycount()*100);
								syschanel = 99;
								f = true;
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							logs.printErrorLog(e);
						}
						
					}else{//旧系统
						logs.putParam("systype", "oldsys");
						OldOrderBFGEntity ofg = null;
						try {
							ofg = RSBLL.rb.getOldOrderBFGService().loadOrderbyid(orderid);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if(ofg != null && ofg.getPaystate() != 2){
							pcount = (long)(ofg.getPaymoneycount()*100);
							f = true;
						}
					}
					if(f){
						try {
							ret = PayBuz.pb.startPayByrecorde(orderid, pcount, oid,PayContents.pay_wx_native,syschanel);
						} catch (Exception e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
							logs.printErrorLog(e1);
						}
					}
					
					logs.putParam("startpayret", ret);
				}else{
					
				}
				logs.printInfoLog();
				//System.out.println("ret ret ret "+ret);
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
}
