package model;

import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.Base64.Decoder;
import java.util.Map.Entry;

import org.apache.commons.collections4.Get;
import org.apache.commons.lang3.ObjectUtils.Null;
import org.asynchttpclient.netty.ws.NettyWebSocket;
import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.mongodb.util.JSON;
import com.mysql.fabric.xmlrpc.base.Array;

import apps.appsProxy;
import database.db;
import esayhelper.CacheHelper;
import esayhelper.DBHelper;
import esayhelper.JSONHelper;
import esayhelper.StringHelper;
import esayhelper.TimeHelper;
import esayhelper.checkHelper;
import esayhelper.fileHelper;
import esayhelper.formHelper;
import esayhelper.jGrapeFW_Message;
import esayhelper.formHelper.formdef;
import filterword.WordFilter;
import interfaceApplication.ReportGroup;
import interrupt.interrupt;
import nlogger.nlogger;
import offices.excelHelper;
import rpc.execRequest;
import security.codec;
import session.session;
import sms.ruoyaMASDB;

public class ReportModel {
	private static DBHelper report;
	private static formHelper form;
	private JSONObject _obj = new JSONObject();
	private static int appid = appsProxy.appid();
	private List<String> imgList = new ArrayList<>();
	private List<String> videoList = new ArrayList<>();

	static {
		report = ReportModel.getdb();
		form = report.getChecker();
	}

	private static DBHelper getdb() {
		return getdb(appsProxy.configValue());
	}

	private static DBHelper getdb(JSONObject threadifo) {
		DBHelper tmpdb = new DBHelper(threadifo.get("db").toString(), "reportInfo", "_id");
		tmpdb.bind(String.valueOf(appid));
		return tmpdb;
	}

	private db bind() {
		return report.bind(appid + "");
	}

	// 新增
	@SuppressWarnings("unchecked")
	public String Add(JSONObject object) {
		String info = resultMessage(99);
		// JSONObject object = JSONHelper.string2json(infos);
		// 多个字段表示的图片视频，合并成使用一个字段表示
		object = join(object);
		if (object != null) {
			try {
				String userid = "";
				if (object.containsKey("content")) {
					String content = object.get("content").toString();
					if (content.length() > 500) {
						return resultMessage(6);
					}
					object.put("content", codec.encodebase64(content));
				}
				userid = object.get("userid").toString();
				if (("").equals(userid)) {
					return resultMessage(15);
				}
				int mode = Integer.parseInt(object.get("mode").toString());
				switch (mode) {
				case 0:
					NonAnonymous(userid, object);
					break;
				case 1:
					Anonymous(object);
					break;
				}
			} catch (Exception e) {
				info = resultMessage(99);
			}
		}
		return info;
	}

	// 修改
	@SuppressWarnings("unchecked")
	public int Update(String id, JSONObject object) {
		int code = 99;
		if (object != null) {
			try {
				String message = SearchById(id);
				if (JSONHelper.string2json(message) != null) {
					String tip = JSONHelper.string2json(message).get("message").toString();
					JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
					if (records.containsKey("Rgroup")) {
						if (!("").equals(records.get("Rgroup").toString())) {
							getdb().eq("Rgroup", records.get("Rgroup").toString()).data(object).updateAll();
						}
					}
					if (object.containsKey("reason")) {
						String content = codec.encodebase64(object.get("reason").toString());
						object.put("reason", codec.DecodeHtmlTag(content));
					}
					code = getdb().eq("_id", new ObjectId(id)).data(object).update() != null ? 0 : 99;
				}
			} catch (Exception e) {
				code = 99;
			}
		}
		return code;
	}

	// 删除
	public int Delete(String id) {
		int code = 99;
		try {
			JSONObject object = getdb().eq("_id", new ObjectId(id)).delete();
			code = (object != null ? 0 : 99);
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return code;
	}

	// 删除被拒绝的举报件
	public int Delete() {
		String code = String.valueOf(getdb().eq("state", 3).deleteAll());
		return Integer.parseInt(code);
	}

	// 批量删除
	public int Delete(String[] ids) {
		int code = 99;
		try {
			int len = ids.length;
			getdb().or();
			for (int i = 0; i < len; i++) {
				getdb().eq("_id", new ObjectId(ids[i]));
			}
			long codes = bind().deleteAll();
			code = (Integer.parseInt(String.valueOf(codes)) == len ? 0 : 99);
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return code;
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String page(int ids, int pageSize) {
		Object objects = execRequest.getChannelValue("sid");
		JSONObject obj = getSessPlv(objects);
		JSONObject object = null;
		if (obj != null) {
			try {
				object = new JSONObject();
				JSONArray array = new JSONArray();
				// 获取角色权限
				int roleplv = Integer.parseInt(obj.get("rolePlv").toString());
				if (roleplv > 10000) {
					array = bind().desc("time").page(ids, pageSize);
				}
				if (roleplv > 5000 && roleplv <= 10000) {
					array = bind().desc("time").eq("wbid", (String) obj.get("currentWeb")).page(ids, pageSize);
				}
				if (roleplv > 3000 && roleplv <= 5000) {
					JSONObject oid = (JSONObject) obj.get("_id");
					array = bind().like("ownid", oid.get("$oid").toString()).desc("time")
							.eq("wbid", (String) obj.get("currentWeb")).page(ids, pageSize);
				}
				// JSONArray array = getdb().desc("time").page(ids, pageSize);
				JSONArray array2 = dencode(array); // 获取举报信息图片或者视频等
				object.put("totalSize", (int) Math.ceil((double) getdb().count() / pageSize));
				object.put("pageSize", pageSize);
				object.put("currentPage", ids);
				object.put("data", getImg(array2));
			} catch (Exception e) {
				nlogger.logout(e);
				object = null;
			}
		}
		return resultMessage(object);
	}

	@SuppressWarnings("unchecked")
	private JSONArray dencode(JSONArray array) {
		JSONArray arry = null;
		try {
			if (array.size() == 0) {
				return array;
			}
			arry = new JSONArray();
			for (int i = 0; i < array.size(); i++) {
				JSONObject object = (JSONObject) array.get(i);
				if (object.containsKey("content") && !("").equals(object.get("content").toString())) {
					object.put("content", codec.decodebase64(object.get("content").toString()));
				}
				if (object.containsKey("reason") && !("").equals(object.get("reason").toString())) {
					object.put("reason", codec.decodebase64(object.get("reason").toString()));
				}
				arry.add(object);
			}
		} catch (Exception e) {
			nlogger.logout(e);
			array = null;
		}
		return arry;
	}

	@SuppressWarnings("unchecked")
	private JSONObject dencode(JSONObject obj) {
		if (obj.containsKey("content") && obj.get("content") != "") {
			obj.put("content", codec.decodebase64(obj.get("content").toString()));
		}
		if (obj.containsKey("reason") && obj.get("reason") != "") {
			obj.put("reason", codec.decodebase64(obj.get("reason").toString()));
		}
		return obj;
	}

	// 条件分页
	@SuppressWarnings("unchecked")
	public String page(int ids, int pageSize, JSONObject objects) {
		JSONObject object = null;
		if (objects != null) {
			try {
				getdb().and();
				for (Object obj : objects.keySet()) {
					if (obj.equals("_id")) {
						getdb().eq("_id", new ObjectId(objects.get("_id").toString()));
					}
					getdb().eq(obj.toString(), objects.get(obj.toString()));
				}
				object = new JSONObject();
				JSONArray array = getdb().dirty().desc("time").page(ids, pageSize);
				if (array.size() == 0) {
					object.put("data", array);
				} else {
					JSONArray array2 = dencode(array);
					object.put("data", getImg(array2));
				}
				object.put("totalSize", (int) Math.ceil((double) getdb().count() / pageSize));
				object.put("pageSize", pageSize);
				object.put("currentPage", ids);
			} catch (Exception e) {
				nlogger.logout(e);
				object = null;
			}
		}
		return resultMessage(object);
	}

	// 模糊查询
	@SuppressWarnings("unchecked")
	public String find(int ids, int pageSize, JSONObject objects) {
		JSONObject object = null;
		if (objects != null) {
			try {
				getdb().or();
				for (Object obj : objects.keySet()) {
					getdb().like(obj.toString(), objects.get(obj.toString()));
				}
				JSONArray array = getdb().dirty().page(ids, pageSize);
				JSONArray array2 = dencode(array);
				object = new JSONObject();
				object.put("totalSize", (int) Math.ceil((double) getdb().count() / pageSize));
				object.put("pageSize", pageSize);
				object.put("currentPage", ids);
				object.put("data", getImg(array2));
			} catch (Exception e) {
				object = null;
			}
		}
		return resultMessage(object);
	}

	// 批量查询
	public String Select(JSONObject object, int no) {
		JSONArray array = null;
		if (object != null) {
			try {
				getdb().and();
				for (Object obj : object.keySet()) {
					if (obj.equals("_id")) {
						getdb().eq("_id", new ObjectId(object.get("_id").toString()));
					}
					String value = object.get(obj.toString()).toString();
					if (value.contains(",")) {
						getCond(report, obj.toString(), value.split(","));
					} else {
						getdb().eq(obj.toString(), object.get(obj.toString()).toString());
					}
				}
				array = new JSONArray();
				array = getdb().limit(no).select();
			} catch (Exception e) {
				array = null;
			}
		}
		return resultMessage(getImg(dencode(array)));
	}

	private DBHelper getCond(DBHelper rep, String key, String[] values) {
		rep.or();
		for (int i = 0; i < values.length; i++) {
			rep.eq(key, Long.parseLong(values[i]));
		}
		return rep;
	}

	public String finds(JSONObject object) {
		JSONArray array = null;
		try {
			array = new JSONArray();
			if (object == null) {
				getdb().eq("state", 1L);
			} else {
				getdb().and();
				for (Object obj : object.keySet()) {
					if (obj.equals("_id")) {
						getdb().eq("_id", new ObjectId(object.get("_id").toString()));
					}
					if (obj.equals("state")) {
						getdb().eq(obj.toString(), Long.parseLong(object.get(obj.toString()).toString()));
					} else {
						getdb().like(obj.toString(), object.get(obj.toString()).toString());
					}
				}
			}
			array = getdb().limit(50).select();
		} catch (Exception e) {
			array = null;
		}
		return resultMessage(getImg(dencode(array)));
	}

	public JSONArray findexcel(JSONObject object) {
		if (object == null) {
			getdb().eq("state", 1L);
		} else {
			getdb().and();
			for (Object obj : object.keySet()) {
				if (obj.equals("_id")) {
					getdb().eq("_id", new ObjectId(object.get("_id").toString()));
				}
				if (obj.equals("state")) {
					getdb().eq(obj.toString(), Long.parseLong(object.get(obj.toString()).toString()));
				} else {
					getdb().like(obj.toString(), object.get(obj.toString()).toString());
				}
			}
		}
		JSONArray array = getdb().limit(50).select();
		return getImg(dencode(array));
	}

	// 举报件处理完成
	@SuppressWarnings("unchecked")
	public int Complete(String id, JSONObject reasons) {
		int code = 99;
		if (reasons != null) {
			try {
				if (!reasons.containsKey("state")) {
					// if (!("2").equals(reasons.get("state").toString())) {
					reasons.put("state", 2);
					// }
				}
				if (!reasons.containsKey("completetime")) {
					// if (("").equals(reasons.get("completetime").toString()))
					// {
					reasons.put("completetime", String.valueOf(TimeHelper.nowMillis()));
					// }
				}
				if (reasons.containsKey("reason") && !("").equals(reasons.get("reason").toString())) {
					reasons.put("reason", codec.DecodeHtmlTag(reasons.get("reason").toString()));
				}
				String message = SearchById(id);
				if (JSONHelper.string2json(message) != null) {
					String tip = JSONHelper.string2json(message).get("message").toString();
					JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
					if (records.containsKey("Rgroup")) {
						if (!("").equals(records.get("Rgroup").toString())) {
							getdb().eq("Rgroup", records.get("Rgroup").toString()).data(reasons).updateAll();
						} else {
							code = getdb().eq("_id", new ObjectId(id)).data(reasons).update() != null ? 0 : 99;
						}
					} else {
						code = getdb().eq("_id", new ObjectId(id)).data(reasons).update() != null ? 0 : 99;
					}
				}
			} catch (Exception e) {
				code = 99;
			}
		}
		return code;
	}

	// 举报拒绝
	@SuppressWarnings("unchecked")
	public int Refuse(String id, JSONObject reasons) {
		int code = 99;
		if (reasons != null) {
			try {
				if (!reasons.containsKey("state")) {
					reasons.put("state", 3);
				}
				if (!reasons.containsKey("refusetime")) {
					reasons.put("refusetime", String.valueOf(TimeHelper.nowMillis()));
				}
				if (!reasons.containsKey("isdelete")) {
					reasons.put("isdelete", 1);
				}
				String message = SearchById(id);
				if (JSONHelper.string2json(message) != null) {
					String tip = JSONHelper.string2json(message).get("message").toString();
					if (!("").equals(tip)) {
						JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
						if (records.containsKey("Rgroup")) {
							if (!("").equals(records.get("Rgroup").toString())) {
								getdb().eq("Rgroup", records.get("Rgroup").toString()).data(reasons).updateAll();
							} else {
								code = getdb().eq("_id", new ObjectId(id)).data(reasons).update() != null ? 0 : 99;
							}
						} else {
							code = getdb().eq("_id", new ObjectId(id)).data(reasons).update() != null ? 0 : 99;
						}
					}
				}
			} catch (Exception e) {
				code = 99;
			}
		}
		return code;
	}

	// 发送处理件短信至举报人
	private void SendSMS(String id) {
		String message = SearchById(id);
		String msg = JSONHelper.string2json(message).get("message").toString();
		JSONObject object = JSONHelper.string2json(msg);
		if (object.containsKey("InformantPhone")) {
			String phone = object.get("InformantPhone").toString();
			String text = "";
			if ("3".equals(object.get("state").toString())) {
				text = "您举报的" + object.get("content").toString() + "已被拒绝," + "拒绝理由为：" + object.get("reason").toString();
			}
			if ("2".equals(object.get("state").toString())) {
				text = "您举报的" + object.get("content").toString() + "已处理完成";
			}
			ruoyaMASDB.sendSMS(phone, text);
		}
	}

	@SuppressWarnings("unchecked")
	public int SendVerity(String phone, String text) {
		session session = new session();
		String time = "";
		String currenttime = TimeHelper.stampToDate(TimeHelper.nowMillis()).split(" ")[0];
		int count = 0;
		JSONObject object = new JSONObject();
		if (session.get(phone) != null) {
			object = JSONHelper.string2json(session.get(phone).toString());
			count = Integer.parseInt(object.get("count").toString()); // 次数
			time = TimeHelper.stampToDate(Long.parseLong(object.get("time").toString()));
			time = time.split(" ")[0];
			if (currenttime.equals(time) && count == 5) {
				return 11;
			}
		}
		String tip = ruoyaMASDB.sendSMS(phone, text);
		count++;
		object.put("count", count + "");
		object.put("time", TimeHelper.nowMillis());
		session.setget(phone, object);
		return tip != null ? 0 : 99;
	}

	// 查询个人相关的举报件
	public String search(String userid, int no) {
		JSONArray array = null;
		try {
			array = new JSONArray();
			array = getdb().and().eq("userid", userid).ne("state", 0).limit(no).select();
		} catch (Exception e) {
			array = null;
		}
		JSONArray array2 = getImg(array);
		return resultMessage(array2);
	}

	public String counts(String userid) {
		String count = "";
		JSONArray array = getdb().count("userid").group("userid");
		JSONObject obj = new JSONObject();
		for (int i = 0; i < array.size(); i++) {
			obj = (JSONObject) array.get(i);
			if (obj.get("_id") == null) {
				continue;
			}
			if (!userid.equals(obj.get("_id").toString())) {
				continue;
			}
			count = String.valueOf(obj.get("count").toString());
		}
		// return resultMessage(0, String.valueOf(count));
		return resultMessage(0, ("").equals(count) ? "0" : count);
	}

	@SuppressWarnings("unchecked")
	public String counts() {
		// long count = getdb().eq("state", 0).count();
		int count = 0;
		JSONArray array = getdb().count("state").group("state");
		JSONObject obj = new JSONObject();
		JSONObject objs = new JSONObject();
		objs.put("$numberLong", "0");
		for (int i = 0; i < array.size(); i++) {
			obj = (JSONObject) array.get(i);
			if (Integer.parseInt(obj.get("_id").toString()) != 0
					&& !obj.get("_id").toString().equals(objs.toString())) {
				continue;
			}
			count = Integer.parseInt(obj.get("count").toString());
		}
		// return resultMessage(0, String.valueOf(count));
		return resultMessage(0, String.valueOf(count));
	}

	public String feed(String userid) {
		long count = getdb().eq("userid", userid).ne("state", 0).count();
		return resultMessage(0, String.valueOf(count));
	}

	// 导出举报件
	public String print(String info) {
		String Date = TimeHelper.stampToDate(TimeHelper.nowMillis()).split(" ")[0];
		File file = excelHelper.out("GrapeReport/Report/excel/" + info);
		if (file == null) {
			return resultMessage(0, "没有符合条件的数据");
		}
		String uuid = UUID.randomUUID().toString();
		File tarFile = new File("\file\\tomcat\\webapps\\File\\upload\\" + Date + "\\Grape" + uuid + ".xls");
		file.renameTo(tarFile);
		String target = tarFile.toString();
		String hoString = "http://";
		target = hoString + getAppIp("file").split("/")[1] + target.split("webapps")[1];
		return resultMessage(0, target);
	}

	// 显示举报信息，包含上一篇，下一篇
	@SuppressWarnings("unchecked")
	public String SearchById(String id) {
		JSONObject object = bind().eq("_id", new ObjectId(id)).find();
		if (object == null) {
			return resultMessage(0, "无符合条件的数据");
		}
		JSONObject preobj = find(object.get("time").toString(), "<");
		JSONObject nextobj = find(object.get("time").toString(), ">");
		object.put("previd", getpnReport(preobj).get("id"));
		object.put("prevname", getpnReport(preobj).get("name"));
		object.put("nextid", getpnReport(nextobj).get("id"));
		object.put("nextname", getpnReport(nextobj).get("name"));
		return resultMessage(getImg(dencode(object)));
	}

	public String insert(JSONObject info) {
		String tip = null;
		if (info != null) {
			try {
				tip = getdb().data(info).insertOnce().toString();
			} catch (Exception e) {
				tip = null;
			}
		}
		return tip != null ? tip : "";
	}

	// 实名举报
	public String RealName(JSONObject object) {
		String openid = object.get("userid").toString();
		// 判断是否实名认证
		String message = appsProxy.proxyCall(callHost(), appid + "/16/wechatUser/FindOpenId/" + openid, null, "")
				.toString();
		String tip = JSONHelper.string2json(message).get("message").toString();
		if (!("").equals(tip)) {
			String phone = JSONHelper.string2json(tip).get("phone").toString();
			String ckcode = getValidateCode();
			// 1.发送验证码
			int code = SendVerity(phone, "验证码:" + ckcode);
			if (code == 0) {
				String nextstep = appid + "/45/Report/insert/" + object.toString();
				JSONObject object2 = interrupt._exist(phone, String.valueOf(appid));
				if (object2 != null) {
					interrupt._clear(phone, String.valueOf(appid));
				}
				boolean flag = interrupt._break(ckcode, phone, nextstep, appid + "");
				code = flag ? 0 : 99;
			}
			return resultMessage(code, "验证码发送成功");
		}
		return resultMessage(99);
	}

	// 恢复当前操作
	@SuppressWarnings("unchecked")
	public String resu(JSONObject object) {
		// String message = "";
		session session = new session();
		String openid = object.get("openid").toString();
		// 判断是否实名认证
		String messages = appsProxy.proxyCall(callHost(), appid + "/16/wechatUser/FindOpenId/" + openid, null, "")
				.toString();
		if (JSONHelper.string2json(messages) != null) {
			String tips = JSONHelper.string2json(messages).get("message").toString();
			// if (!("").equals(tips)) {
			// return resultMessage(14);
			// }
			if (!object.containsKey("phone")) {
				String phone = JSONHelper.string2json(tips).get("phone").toString();
				object.put("phone", phone);
			}
		}
		// if (object == null) {
		// return resultMessage(10);
		// }
		if (object.containsKey("ckcode") && object.containsKey("phone")) {
			int code = interrupt._resume(object.get("ckcode").toString(), object.get("phone").toString(),
					String.valueOf(appsProxy.appid()));
			if (code == 0) {
				return resultMessage(4);
			}
			if (code == 1) {
				return resultMessage(5);
			}
			if (("2").equals(object.get("type").toString())) {
				String url = appid + "/45/Report/insert/s:" + session.get(object.get("openid").toString());
				String tip = appsProxy.proxyCall(callHost(), url, null, "").toString();
				// String message = appsProxy.proxyCall(host,
				// "/15/wechatUser/FindById/"
				// + object.get("openid").toString(),
				// null, "").toString();
				// return message;
			}
			// message = appsProxy.proxyCall(callHost(), appid +
			// "/16/wechatUser/FindOpenId/" + openid, null, "")
			// .toString();
		}
		// nlogger.logout(" resume message" + message);
		return messages;
	}

	// 验证内容是否含有敏感字符串
	public int checkCont(String content) {
		if (WordFilter.isContains(content)) {
			return 3;
		}
		return 0;
	}

	// 获取用户openid，实名认证
	@SuppressWarnings("unchecked")
	public String getId(String code, String url) {
		JSONObject object = new JSONObject();
		if (code == null) {
			object.put("error", "code错误");
			return jGrapeFW_Message.netMSG(1, object.toString());
		}
		// 获取微信签名
		String signMsg = appsProxy.proxyCall(callHost(), appid + "/30/Wechat/getSignature/" + url, null, "").toString();
		String sign = JSONHelper.string2json(signMsg).get("message").toString();
		String openid = appsProxy.proxyCall(callHost(), appid + "/30/Wechat/BindWechat/" + code, null, "").toString();
		if (openid.equals("")) {
			return jGrapeFW_Message.netMSG(7, "code错误");
		}
		// 将获取到的openid与库表中的openid进行比对，若存在已绑定，否则未绑定
		String message = appsProxy.proxyCall(callHost(), appid + "/16/wechatUser/FindOpenId/" + openid, null, "")
				.toString();
		nlogger.logout("message:" + message);
		String tip = JSONHelper.string2json(message).get("message").toString();
		if (!("").equals(tip)) {
			object.put("msg", "已实名认证");
			object.put("openid", openid);
			object.put("headimgurl", JSONHelper.string2json(tip).get("headimgurl").toString());
			object.put("sign", sign);
			return jGrapeFW_Message.netMSG(0, object.toString());
		}
		object.put("msg", "未实名认证");
		object.put("openid", openid);
		object.put("sign", sign);
		return jGrapeFW_Message.netMSG(1, object.toString());
	}

	// 实名认证
	public int Certify(String info) {
		// System.out.println(info);
		JSONObject object = JSONHelper.string2json(info);
		if (object == null) {
			return 10;
		}
		if (!object.containsKey("openid")) {
			return 8;
		}
		String phone = object.get("phone").toString();
		if (!checkPhone(phone)) {
			return 5;
		}
		// 发送短信验证码,中断当前操作
		// 获取随机6位验证码
		String ckcode = getValidateCode();
		// 1.发送验证码
		// String text = "您的验证码为：" + ckcode;

		int code = SendVerity(object.get("phone").toString(), "验证码：" + ckcode);
		if (code == 0) {
			String nextstep = appid + "/16/wechatUser/insertOpenId/" + info;
			// 2.中断[参数：随机验证码，手机号，下一步操作，appid]
			JSONObject object2 = interrupt._exist(phone, String.valueOf(appid));
			if (object2 != null) {
				interrupt._clear(phone, String.valueOf(appid));
			}
			boolean flag = interrupt._break(ckcode, phone, nextstep, appid + "");
			code = flag ? 0 : 99;
			// if (("2").equals(object.get("type").toString())) {
			// if (code == 0) {
			// info = session.get(object.get("openid").toString())
			// .toString();
			// System.out.println(info.getBytes().length);
			// nlogger.logout(info);
			//// String step = info.replace("\"", "\\\"");
			// interrupt._break(ckcode, object.get("phone").toString(),
			// appid + "/45/Report/insert/s:" + info, appid + "");
			// code = flag ? 0 : 99;
			// }
			// }
		}
		return code;
	}

	// 用户封号
	@SuppressWarnings("unchecked")
	public String UserKick(String openid, JSONObject object) {
		if (!object.containsKey("isdelete")) {
			object.put("isdelete", "1");
		}
		if (!object.containsKey("time")) {
			object.put("time", TimeHelper.nowMillis() + "");
		}
		return appsProxy
				.proxyCall(callHost(), appid + "/16/wechatUser/KickUser/" + openid + "/" + object.toString(), null, "")
				.toString();
	}

	// 解封
	public String UserUnKick() {
		return appsProxy.proxyCall(callHost(), appid + "/16/wechatUser/unkick/", null, "").toString();
	}

	// 举报量统计
	public String EventCounts(JSONObject object) {
		db db = setCondString(getdb().and(), object);
		return resultMessage(0, String.valueOf(db.count()));
	}

	// 举报量与全量比例
	public String PercentCounts(JSONObject object) {
		float count = (float) getdb().count();
		db db = setCondString(getdb().and(), object);
		float Eventcount = (float) db.count();
		return resultMessage(0, String.format("%.2f", (double) (Eventcount / count)));
	}

	// 定时发送新增举报量到管理员手机号
	public String TimerSend(JSONObject object) {

		long count = getdb().count();
		db db = setCondString(getdb().and(), object);
		long Eventcount = db.count();
		return resultMessage(0, String.format("%.2f", Eventcount / count));
	}

	// 统计24小时内新增举报量
	@SuppressWarnings("unchecked")
	public String TimerInsertCount(JSONObject threadInfo) {
		long InsertCount = 0;
		DBHelper tmpdb = getdb(threadInfo);
		if (tmpdb != null) {
			long currentTime = TimeHelper.nowMillis();
			String OpTime = String.valueOf(currentTime - 24 * 3600 * 1000);
			JSONObject object = new JSONObject();
			object.put("time", OpTime + "~" + String.valueOf(currentTime));
			JSONObject objects = getTime(object.get("time").toString());
			InsertCount = tmpdb.gte("time", objects.get("start").toString()).lte("time", objects.get("end").toString())
					.count();
		}
		return String.valueOf(InsertCount);
	}

	@SuppressWarnings("unchecked")
	public int ReportJoin(String[] ids) {
		List<String> list = new ArrayList<>();
		JSONObject object = new JSONObject();
		getdb().or();
		for (int i = 0; i < ids.length; i++) {
			String message = SearchById(ids[i]);
			String tip = JSONHelper.string2json(message).get("message").toString();
			JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
			if (records.containsKey("Rgroup")) {
				if (!(" ").equals(records.get("Rgroup").toString())) {
					return 17;
				}
			}
			getdb().eq("_id", new ObjectId(ids[i]));
			if (!judgeState(ids[i])) {
				return 16;
			}
			list.add(ids[i]);
		}
		object.put("content", StringHelper.join(list));
		// 新增到事件组，获取组id
		String message = appsProxy
				.proxyCall(callHost(), appid + "/45/ReportGroup/AddRgroup" + object.toString(), null, "").toString();
		String Rgroup = JSONHelper.string2json(message).get("message").toString();
		if (!("").equals(Rgroup)) {
			JSONObject objects = new JSONObject();
			objects.put("Rgroup", Rgroup);
			getdb().data(objects).updateAll();
			return 0;
		}
		return 99;
	}

	// 获取会话信息数据
	@SuppressWarnings("unchecked")
	private JSONObject getSessPlv(Object object) {
		JSONObject object2 = null;
		session session = new session();
		try {
			JSONObject objects = new JSONObject();
			int roleplv = 0;
			object2 = new JSONObject();
			if (object != null) {
				object2 = session.getSession(object.toString());
				if (object2 != null) {
					String info = appsProxy
							.proxyCall(getAppIp("host").split("/")[0],
									appsProxy.appid() + "/16/roles/getRole/" + object2.get("ugid").toString(), null, "")
							.toString();
					objects = JSONHelper.string2json(info);
					if (objects != null) {
						objects = JSONHelper.string2json(objects.get("message").toString());
					}
					if (objects != null) {
						objects = JSONHelper.string2json(objects.get("records").toString());
					}
					if (objects != null) {
						roleplv = Integer.parseInt(objects.get("plv").toString());
					}
				}
				object2.put("rolePlv", roleplv);
			} else {
				object2.put("rolePlv", 0);
			}
		} catch (Exception e) {
			nlogger.logout(e);
			object2 = null;
		}
		return object2;
	}

	private String callHost() {
		return getAppIp("host").split("/")[0];
	}

	private String outerHost() {
		return getAppIp("host").split("/")[1];
	}

	private String getAppIp(String key) {
		String value = "";
		try {
			Properties pro = new Properties();
			pro.load(new FileInputStream("URLConfig.properties"));
			value = pro.getProperty(key);
		} catch (Exception e) {
			value = "";
		}
		return value;
	}

	private boolean judgeState(String id) {
		session session = new session();
		String message = SearchById(id);
		String tip = JSONHelper.string2json(message).get("message").toString();
		JSONObject records = (JSONObject) JSONHelper.string2json(tip).get("records");
		String state = records.get("state").toString();
		if (session.get("state") != null) {
			if (!session.get("state").toString().equals(state)) {
				return false;
			}
		}
		session.setget("state", state);
		return true;
	}

	// 统计量与统计量所有量比例条件设置
	private db setCondString(db db, JSONObject object) {
		for (Object obj : object.keySet()) {
			if ("time".equals(obj.toString()) || "handletime".equals(obj.toString())
					|| "completetime".equals(obj.toString()) || "refusetime".equals(obj.toString())) {
				JSONObject objects = getTime(object.get(obj.toString()).toString());
				db = getdb().gte(obj.toString(), objects.get("start").toString()).lte(obj.toString(),
						objects.get("start").toString());
			} else {
				db = getdb().eq(obj.toString(), object.get(obj.toString()));
			}
		}
		return db;
	}

	// 获取时间区间开始时间和结束时间
	@SuppressWarnings("unchecked")
	private JSONObject getTime(String times) {
		JSONObject object = new JSONObject();
		long starts = 0, ends = 0;
		String start = times.split("~")[0];
		String end = times.split("~")[1];
		if (start.contains(" ")) {
			try {
				starts = TimeHelper.dateToStamp(start);
				ends = TimeHelper.dateToStamp(start);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		} else {
			starts = Long.parseLong(start);
			ends = Long.parseLong(end);
		}
		object.put("start", starts);
		object.put("end", ends);
		return object;
	}

	// 获取举报信息[上一条，下一条显示]
	@SuppressWarnings("unchecked")
	private JSONObject getpnReport(JSONObject object) {
		String id = null;
		String name = null;
		JSONObject object2 = new JSONObject();
		if (object != null) {
			JSONObject obj = (JSONObject) object.get("_id");
			id = obj.get("$oid").toString();
			name = object.get("content").toString();
			object2.put("id", id);
			object2.put("name", name);
		}
		return object2;
	}

	private JSONObject find(String time, String logic) {
		if (time.contains("$numberLong")) {
			JSONObject object = JSONHelper.string2json(time);
			time = object.get("$numberLong").toString();
		}
		if (logic == "<") {
			getdb().lt("time", time).desc("time");
		} else {
			getdb().gt("time", time).asc("time");
		}
		return getdb().find();
	}

	@SuppressWarnings("unchecked")
	private JSONArray getImg(JSONArray array) {
		JSONArray array2 = null;
		if (array != null) {
			try {
				array2 = new JSONArray();
				if (array.size() == 0) {
					return array;
				}
				for (int i = 0; i < array.size(); i++) {
					JSONObject object = (JSONObject) array.get(i);
					array2.add(getImg(object));
				}
			} catch (Exception e) {
				array2 = null;
			}
		}
		return array2;
	}

	@SuppressWarnings("unchecked")
	// private JSONObject getImg(JSONObject object) {
	// if (object != null && !("").equals(object)) {
	// List<String> liStrings = new ArrayList<String>();
	// if (object.containsKey("attr1")) {
	// object = getFile("attrFile1", object.get("attr1").toString(), object);
	// }
	// if (object.containsKey("attr2")) {
	// object = getFile("attrFile2", object.get("attr2").toString(), object);
	// }
	// if (object.containsKey("attr3")) {
	// object = getFile("attrFile3", object.get("attr3").toString(), object);
	// }
	// if (object.containsKey("attr4")) {
	// object = getFile("attrFile4", object.get("attr4").toString(), object);
	// }
	// if (object.containsKey("mediaid")) {
	// liStrings = getMedia(liStrings, object.get("mediaid").toString());
	// }
	// // 获取举报类型
	// if (object.containsKey("type")) {
	// object = getType(object);
	// }
	// if (imgList.size() != 0) {
	// object.put("image", StringHelper.join(imgList));
	// } else {
	// object.put("image", "");
	// }
	// if (videoList.size() != 0) {
	// object.put("video", StringHelper.join(videoList));
	// } else {
	// object.put("video", "");
	// }
	// if (liStrings.size() != 0) {
	// object.put("media", StringHelper.join(liStrings));
	// } else {
	// object.put("media", "");
	// }
	// }
	// return object;
	// }
	private JSONObject getImg(JSONObject object) {
		if (object != null) {
			List<String> liStrings = new ArrayList<String>();
			try {
				if (object.containsKey("attr") && !("").equals(object.get("attr").toString())) {
					String attr = object.get("attr").toString();
					if (attr.contains(",")) {
						String[] value = attr.split(",");
						for (int i = 0; i < value.length; i++) {
							object = getFile("attrFile" + i, value[i], object);
						}
					}
				}
				if (object.containsKey("mediaid")) {
					liStrings = getMedia(liStrings, object.get("mediaid").toString());
				}
				// 获取举报类型
				if (object.containsKey("type")) {
					object = getType(object);
				}
				if (imgList.size() != 0) {
					object.put("image", StringHelper.join(imgList));
				} else {
					object.put("image", "");
				}
				if (videoList.size() != 0) {
					object.put("video", StringHelper.join(videoList));
				} else {
					object.put("video", "");
				}
				if (liStrings.size() != 0) {
					object.put("media", StringHelper.join(liStrings));
				} else {
					object.put("media", "");
				}
			} catch (Exception e) {
				object = null;
			}
		}
		return object;
	}

	private List<String> getMedia(List<String> list, String mediaid) {
		if (("").equals(mediaid)) {
			return list;
		}
		String message = appsProxy.proxyCall(callHost(), appid + "/30/Wechat/downloadMedia/" + mediaid, null, "")
				.toString();
		if (JSONHelper.string2json(message) != null) {
			String url = JSONHelper.string2json(message).get("message").toString();
			url = outerHost() + url;
			if (!("").equals(url)) {
				list.add(url);
			}
		}

		return list;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getType(JSONObject object) {
		String msg = "";
		session session = new session();
		if (object != null) {
			if (object.get("type") == null) {
				object.put("ReportType", "测试数据");
			} else if (("0").equals(object.get("type").toString())) {
				object.put("ReportType", "");
			} else {
				if (session.get(object.get("type").toString()) != null) {
					msg = session.get(object.get("type").toString()).toString();
					if (("").equals(msg)) {
						object.put("ReportType", "");
					} else {
						object.put("ReportType", JSONHelper.string2json(msg).get("TypeName").toString());
					}
				} else {
					String message = appsProxy
							.proxyCall(callHost(),
									appsProxy.appid() + "/45/Rtype/findById/" + object.get("type").toString(), null, "")
							.toString();
					msg = JSONHelper.string2json(message).get("message").toString();
					if (("").equals(msg)) {
						object.put("ReportType", "");
					} else {
						msg = JSONHelper.string2json(msg).get("records").toString();
						object.put("ReportType", JSONHelper.string2json(msg).get("TypeName").toString());
					}
					session.setget(object.get("type").toString(), msg);
				}
			}
		}
		return object;
	}

	// private List<String> getImgUrl(List<String> list, String imgId) {
	// if (("").equals(imgId)) {
	// return list;
	// }
	// String url = appsProxy.proxyCall(host, appid + "/24/Files/geturl/" +
	// imgId, null, "").toString();
	// if (!("").equals(url)) {
	// list.add(url);
	// }
	// return list;
	// }

	@SuppressWarnings("unchecked")
	private JSONObject getFile(String key, String imgid, JSONObject object) {
		CacheHelper cache = new CacheHelper();
		if (object != null && !("").equals(object)) {
			if (!("").equals(imgid)) {
				String fileInfo = "";
				if (cache.get(imgid) != null) {
					fileInfo = cache.get(imgid).toString();
				} else {
					// 获取文件对象getAppIp("file")
					String imgurl = "http://" + getAppIp("file").split("/")[1];
					fileInfo = appsProxy.proxyCall(imgurl, appid + "/24/Files/getFile/" + imgid, null, "").toString();
					if (!("").equals(fileInfo) && fileInfo != null) {
						fileInfo = JSONHelper.string2json(fileInfo).get("message").toString();
						cache.setget(imgid, fileInfo);
					}
				}
				if (("").equals(fileInfo)) {
					object.put(key, "");
				} else {
					JSONObject object2 = JSONHelper.string2json(fileInfo);
					object.put(key, object2);
					if ("1".equals(object2.get("filetype").toString())) {
						// imgList.add("http://123.57.214.226:8080" +
						// object2.get("filepath").toString());
						imgList.add("http://" + getAppIp("file").split("/")[1] + object2.get("filepath").toString());
					}
					if ("2".equals(object2.get("filetype").toString())) {
						videoList.add("http://" + getAppIp("file").split("/")[1] + object2.get("filepath").toString());
					}
				}
			}
		}
		return object;
	}

	private boolean checkPhone(String mob) {
		return checkHelper.checkMobileNumber(mob);
	}

	// 合并举报件附件（视频，音频）
	@SuppressWarnings("unchecked")
	private JSONObject join(JSONObject object) {
		List<String> list = null;
		if (object != null) {
			try {
				list = new ArrayList<String>();
				if (object.containsKey("attr1") && !("").equals(object.get("attr1").toString())) {
					list.add(object.get("attr1").toString());
				}
				if (object.containsKey("attr2")) {
					list.add(object.get("attr2").toString());
				}
				if (object.containsKey("attr3")) {
					list.add(object.get("attr3").toString());
				}
				if (object.containsKey("attr4")) {
					list.add(object.get("attr4").toString());
				}
				object.remove("attr1");
				object.remove("attr2");
				object.remove("attr3");
				object.remove("attr4");
			} catch (Exception e) {
				nlogger.logout(e);
				list = null;
			}
			if (list != null) {
				object.put("attr", StringHelper.join(list));
			} else {
				object.put("attr", "");
			}
		}
		return object;
	}

	// 实名举报
	private String NonAnonymous(String userid, JSONObject object) {
		String info = resultMessage(99);
		// 判断用户是否被封号
		String message = appsProxy.proxyCall(callHost(), appid + "/16/wechatUser/FindOpenId/" + userid, null, "")
				.toString();
		if (JSONHelper.string2json(message) != null) {
			String msg = JSONHelper.string2json(message).get("message").toString();
			if (JSONHelper.string2json(msg) == null) {
				session session = new session();
				session.setget(object.get("userid").toString(), object.toString());
				return resultMessage(12);
			}
			if (("1").equals(JSONHelper.string2json(msg).get("isdelete").toString())) {
				return resultMessage(9);
			}
			info = RealName(object);
		}
		return info;
	}

	// 匿名举报
	private String Anonymous(JSONObject object) {
		String info = resultMessage(99);
		setCheck();
		if (!form.checkRuleEx(object)) {
			return resultMessage(1);
		}
		info = insert(object);
		if (info == null) {
			info = resultMessage(99);
		} else {
			info = SearchById(info);
		}
		return info != null ? info : resultMessage(0, "匿名举报失败");
	}

	// 设置验证项
	private formHelper setCheck() {
		// form.putRule("Wrongdoer", formdef.notNull);
		form.putRule("WrongdoerSex", formdef.notNull);
		form.putRule("mode", formdef.notNull);
		form.putRule("type", formdef.notNull);
		form.putRule("content", formdef.notNull);
		form.putRule("region", formdef.notNull); // 地区
		// form.putRule("workplace", formdef.notNull);
		// form.putRule("post", formdef.notNull);
		return form;
	}

	private String getValidateCode() {
		String num = "";
		for (int i = 0; i < 6; i++) {
			num = num + String.valueOf((int) Math.floor(Math.random() * 9 + 1));
		}
		return num;
	}

	/**
	 * 将map添加至JSONObject中
	 * 
	 * @param map
	 * @param object
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public JSONObject AddMap(HashMap<String, Object> map, JSONObject object) {
		if (object != null) {
			if (map.entrySet() != null) {
				Iterator<Entry<String, Object>> iterator = map.entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<String, Object> entry = (Map.Entry<String, Object>) iterator.next();
					if (!object.containsKey(entry.getKey())) {
						object.put(entry.getKey(), entry.getValue());
					}
				}
			}
		}
		return object;
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONObject object) {
		if (object == null) {
			object = new JSONObject();
		}
		_obj.put("records", object);
		return resultMessage(0, _obj.toString());
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONArray array) {
		if (array == null) {
			array = new JSONArray();
		}
		_obj.put("records", array);
		return resultMessage(0, _obj.toString());
	}

	private String resultMessage(int num) {
		return resultMessage(num, "");
	}

	public String resultMessage(int num, String message) {
		String msg = "";
		switch (num) {
		case 0:
			msg = message;
			break;
		case 1:
			msg = "必填项为空";
			break;
		case 2:
			msg = "手机号格式错误";
			break;
		case 3:
			msg = "存在敏感字符串";
			break;
		case 4:
			msg = "下一步操作不存在";
			break;
		case 5:
			msg = "验证码错误";
			break;
		case 6:
			msg = "内容超过指定字数";
			break;
		case 8:
			msg = "信息不完整";
			break;
		case 9:
			msg = "举报系统异常，请稍候再试";
			break;
		case 10:
			msg = "参数格式错误";
			break;
		case 11:
			msg = "您今日短信发送次数已达上线";
			break;
		case 12:
			msg = "您还未进行实名绑定";
			break;
		case 13:
			msg = "身份证号格式错误";
			break;
		case 14:
			msg = "您已实名认证过";
			break;
		case 15:
			msg = "无法获取openid";
			break;
		case 16:
			msg = "不同类型的举报件不能进行合并";
			break;
		case 17:
			msg = "该事件已被合并至其他事件";
			break;
		case 18:
			msg = "用户不存在";
			break;
		default:
			msg = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
