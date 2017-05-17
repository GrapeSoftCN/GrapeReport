package interfaceApplication;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.bson.types.ObjectId;
import org.hamcrest.Matcher;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
import esayhelper.DBHelper;
import esayhelper.JSONHelper;
import esayhelper.StringHelper;
import esayhelper.TimeHelper;
import esayhelper.checkHelper;
import esayhelper.formHelper;
import esayhelper.jGrapeFW_Message;
import filterword.WordFilter;
import interrupt.interrupt;
import nlogger.nlogger;
import offices.excelHelper;
import thirdsdk.wechatHelper;
import esayhelper.formHelper.formdef;

public class Report {
	private ScheduledExecutorService service = Executors
			.newSingleThreadScheduledExecutor();
	private static DBHelper report;
	private static formHelper form;
	private HashMap<String, Object> map = new HashMap<>();
	private JSONObject _obj = new JSONObject();

	public Report() {
		map.put("time", String.valueOf(TimeHelper.nowMillis()));
		map.put("state", 0); // 0：已受理；1：处理中；2：已处理；3：被拒绝
		map.put("read", 0);
		map.put("isdelete", 0);
		map.put("mode", 0);
		map.put("reson", "");
	}

	static {
		report = new DBHelper(appsProxy.configValue().get("db").toString(),
				"reportInfo");
		form = report.getChecker();
	}

	// 新增
	public String AddReport(String ReportInfo) {
		JSONObject object = AddMap(map, JSONHelper.string2json(ReportInfo));
		if (!(object.get("content").toString().length() <= 500)) {
			return resultMessage(6);
		}
		int mode = Integer.parseInt(object.get("mode").toString());
		String info = "";
		if (mode == 0) {
			info = RealName(object);
			if (info == null) {
				System.out.println("www");
			}
		}
		if (mode == 1) {
			setCheck();
			if (!form.checkRuleEx(object)) {
				return resultMessage(1);
			}
			info = insert(object.toString());
			if (info == null) {
				info = resultMessage(99);
			} else {
				info = findById(info);
			}
		}
		return info;
	}

	// 修改
	public String UpdateReport(String id, String typeInfo) {
		JSONObject object = JSONHelper.string2json(typeInfo);
		int code = report.eq("_id", new ObjectId(id)).data(object)
				.update() != null ? 0 : 99;
		return resultMessage(code, "修改成功");
	}

	// 删除
	public String DeleteReport(String id) {
		int code = report.eq("_id", new ObjectId(id)).delete() != null ? 0 : 99;
		return resultMessage(code, "删除成功");
	}

	// 删除被拒绝的举报件
	public String DelReport() {
		String code = String.valueOf(report.eq("state", 3).deleteAll());
		return resultMessage(Integer.parseInt(code), "删除成功");
	}

	// 批量删除
	public String DeleteBatchReport(String ids) {
		String[] value = ids.split(",");
		int len = value.length;
		report.or();
		for (int i = 0; i < len; i++) {
			report.eq("_id", new ObjectId(value[i]));
		}
		int code = report.deleteAll() == len ? 0 : 99;
		return resultMessage(code, "删除成功");
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String PageReport(int ids, int pageSize) {
		JSONArray array = report.page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) report.count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(array));
		return resultMessage(object);
	}

	// 模糊查询
	@SuppressWarnings("unchecked")
	public String search(int ids, int pageSize, String info) {
		report.or();
		JSONObject objects = JSONHelper.string2json(info);
		for (Object obj : objects.keySet()) {
			if (obj.equals("_id")) {
				report.like("_id", new ObjectId(objects.get("_id").toString()));
			}
			report.eq(obj.toString(), objects.get(obj.toString()));
		}
		JSONArray array = report.dirty().page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) report.count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", getImg(array));
		return resultMessage(object);
	}

	// 批量查询
	public String BatchSelect(String info, int no) {
		JSONObject object = JSONHelper.string2json(info);
		report.or();
		for (Object obj : object.keySet()) {
			if (obj.equals("_id")) {
				report.like("_id", new ObjectId(object.get("_id").toString()));
			}
			String value = object.get(obj.toString()).toString();
			if (value.contains(",")) {
				getCond(report, obj.toString(), value.split(","));
			} else {
				report.eq(obj.toString(),
						object.get(obj.toString()).toString());
			}

		}
		return resultMessage(getImg(report.limit(no).select()));
	}

	private DBHelper getCond(DBHelper rep, String key, String[] values) {
		for (int i = 0; i < values.length; i++) {
			rep.eq(key, Long.parseLong(values[i]));
		}
		return rep;
	}

	public String find(String info) {
		JSONObject object = JSONHelper.string2json(info);
		if (object == null) {
			report.eq("state", 1L);
		} else {
			for (Object obj : object.keySet()) {
				if (obj.equals("_id")) {
					report.like("_id",
							new ObjectId(object.get("_id").toString()));
				}
				report.like(obj.toString(),
						object.get(obj.toString()).toString());
			}
		}
		return resultMessage(getImg(report.limit(50).select()));
	}

	// 举报件处理完成
	@SuppressWarnings("unchecked")
	public String CompleteReport(String id, String reson) {
		JSONObject reasons = JSONHelper.string2json(reson);
		reasons.put("state", 2);
		appsProxy
				.proxyCall("123.57.214.226:801",
						"45/Reason/addTime/s:"
								+ reasons.get("reason").toString(),
						null, "")
				.toString();
		int code = report.eq("_id", new ObjectId(id)).data(reasons)
				.update() != null ? 0 : 99;
		// 发送至用户微信或短信
		// replyByWechat("你的举报件已处理完成，处理信息为：" +
		// reasons.get("reason").toString());
		return resultMessage(code, "举报件已处理完成");
	}

	// 举报拒绝
	@SuppressWarnings("unchecked")
	public String RefuseReport(String id, String reson) {
		JSONObject reasons = JSONHelper.string2json(reson);
		reasons.put("isdelete", 1);
		reasons.put("state", 3);
		appsProxy
				.proxyCall("123.57.214.226:801",
						"45/Reason/addTime/s:"
								+ reasons.get("reason").toString(),
						null, "")
				.toString();
		int code = report.eq("_id", new ObjectId(id)).data(reasons)
				.update() != null ? 0 : 99;
		// 发送至用户微信或短信
		// replyByWechat("你的举报件已被拒绝处理，处理信息为：" +
		// reasons.get("reason").toString());
		return resultMessage(code, "举报情况不属实，已被拒绝处理");
	}

	// 定时删除被拒绝的举报件（每5天删除被拒绝的任务）
	public String TimerDelete() {
		int delay = 0;
		int period = 5;
		service.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				nlogger.logout(DelReport());
			}
		}, delay, period, TimeUnit.DAYS);
		return "start:" + new Date();
	}

	// 微信回复举报人
	public String replyByWechat(String content) {
		String code = appsProxy.proxyCall("123.57.214.226:801",
				"30/Wechat/ToMsg/1/s:" + content, null, "").toString();
		return code;
	}

	// 短信回复举报人
	public String replyBySMS() {
		// 发送短信
		return null;
	}

	// 查询个人相关的举报件
	public String searchById(String userid, int no) {
		JSONArray array = report.eq("userid", userid).limit(no).select();
		return resultMessage(getImg(array));
	}

	// 查询含有反馈信息但是用户未读的举报件
	public String ShowFeed() {
		JSONArray array = report.eq("state", 2).eq("read", 0).limit(20)
				.select();
		return resultMessage(getImg(array));
	}

	// 导出举报件
	public String printWord(String info) {
		File file = excelHelper.out("45/Report/find/" + info);
		if (file == null) {
			return resultMessage(0, "没有符合条件的数据");
		}
		return resultMessage(0, file.toString());
	}

	public String findById(String id) {
		return resultMessage(getImg(report.eq("_id", new ObjectId(id)).find()));
	}

	private String insert(String info) {
		return report.data(JSONHelper.string2json(info)).insertOnce()
				.toString();
	}

	// 实名举报
	private String RealName(JSONObject object) {
		setCheck();
		form.putRule("Informant", formdef.notNull);
		form.putRule("InformantPhone", formdef.notNull);
		if (!form.checkRuleEx(object)) {
			return resultMessage(1);
		}
		if (!checkPhone(object.get("InformantPhone").toString())) {
			return resultMessage(2);
		}
		// 发送短信验证码,中断当前操作
		// 获取随机6位验证码
		// String ckcode = getValidateCode();
		String ckcode = "23456";
		// 1.发送验证码

		// 2.中断[参数：随机验证码，手机号，下一步操作，appid]
		String nextstep = object.toString().replace("\"", "\\\"");
		boolean flag = interrupt._break(ckcode,
				object.get("InformantPhone").toString(),
				"31/Report/insert/s:" + nextstep, "13");
		return flag ? resultMessage(0, "验证码发送成功") : resultMessage(99);
	}

	// 恢复当前操作
	public String resume(String ckcode, String phone) {
		ckcode = "23456";
		int code = interrupt._resume(ckcode, phone, "13");
		if (code == 0) {
			return resultMessage(4);
		}
		if (code == 1) {
			return resultMessage(5);
		}
		return resultMessage(0, "操作成功");
	}

	// 验证内容是否含有敏感字符串
	public String checkContent(String content) {
		if (WordFilter.isContains(content)) {
			return resultMessage(3);
		}
		return resultMessage(0, "不含敏感字符串");
	}

	// 获取用户openid，实名认证
	@SuppressWarnings("unchecked")
	public String getUserId(String code) {
		System.out.println("111");
		wechatHelper helper = new wechatHelper("wx98fc10d9ac9e0953",
				"63890fa2402f4e6aff5b86d327bf4a37");
		JSONObject object = new JSONObject();
//		String openid = getopid(code);
		String openid = helper.getOpenID(code);
		System.out.println(openid);
		// 将获取到的openid与库表中的openid进行比对，若存在已绑定，否则未绑定
		String message = appsProxy.proxyCall("123.57.214.226:801",
				"16/user/FindOpenId/" + openid, null, "").toString();
		if (message == null) {
			object.put("msg", "已实名认证");
			object.put("openid", openid);
			return jGrapeFW_Message.netMSG(0, object.toString());
		}
		object.put("msg", "未实名认证");
		object.put("openid", openid);
		return jGrapeFW_Message.netMSG(1, object.toString());
	}

	// 获取openid
	private String getopid(String code) {
		String openid = "";
		String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=wx98fc10d9ac9e0953&secret=63890fa2402f4e6aff5b86d327bf4a37&code=" + code
				+ "&grant_type=authorization_code";
		try {
			HttpClient httpClient = new HttpClient();
			GetMethod getMethod = new GetMethod(url);
			int execute = httpClient.executeMethod(getMethod);
			String getResponse = getMethod.getResponseBodyAsString();
			openid = JSONHelper.string2json(getResponse).get("openid")
					.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return openid;
	}

	// 实名认证
	public String Certification(String info) {
		JSONObject object = JSONHelper.string2json(info);
		if (!checkPhone(object.get("phone").toString())) {
			return resultMessage(5);
		}
		if (!checkCard(object)) {
			return resultMessage(6);
		}
		// 发送短信验证码,中断当前操作
		// 获取随机6位验证码
		// String ckcode = getValidateCode();
		String ckcode = "23456";
		// 1.发送验证码

		// 2.中断[参数：随机验证码，手机号，下一步操作，appid]
		String nextstep = object.toString().replace("\"", "\\\"");
		boolean flag = interrupt._break(ckcode,
				object.get("InformantPhone").toString(),
				"16/user/insertOpenId/s:" + nextstep, "13");
		return flag ? resultMessage(0, "验证码发送成功") : resultMessage(99);
	}

	private boolean checkCard(JSONObject object) {
		form.putRule("IDCard", formdef.PersonID);
		return form.checkRuleEx(object);
	}

	@SuppressWarnings("unchecked")
	private JSONArray getImg(JSONArray array) {
		JSONArray array2 = new JSONArray();
		for (int i = 0; i < array.size(); i++) {
			JSONObject object = (JSONObject) array.get(i);
			array2.add(getImg(object));
		}
		return array2;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getImg(JSONObject object) {
		List<String> list = new ArrayList<String>();
		if (object.containsKey("attr1")) {
			list = getImgUrl(list, object.get("attr1").toString());
		}
		if (object.containsKey("attr2")) {
			list = getImgUrl(list, object.get("attr2").toString());
		}
		if (object.containsKey("attr3")) {
			list = getImgUrl(list, object.get("attr3").toString());
		}
		if (object.containsKey("attr4")) {
			list = getImgUrl(list, object.get("attr4").toString());
		}
		object.put("image", StringHelper.join(list));
		return object;
	}

	private List<String> getImgUrl(List<String> list, String imgId) {
		String url = appsProxy.proxyCall("123.57.214.226:801",
				"13/24/Files/geturl/" + imgId, null, "").toString();
		list.add(url);
		return list;
	}

	private boolean checkPhone(String mob) {
		return checkHelper.checkMobileNumber(mob);
	}

	// 设置验证项
	private formHelper setCheck() {
		form.putRule("Wrongdoer", formdef.notNull);
		// form.putRule("WrongdoerSex", formdef.notNull);
		form.putRule("content", formdef.notNull);
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
	private JSONObject AddMap(HashMap<String, Object> map, JSONObject object) {
		if (map.entrySet() != null) {
			Iterator<Entry<String, Object>> iterator = map.entrySet()
					.iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, Object> entry = (Map.Entry<String, Object>) iterator
						.next();
				if (!object.containsKey(entry.getKey())) {
					object.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return object;
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONObject object) {
		_obj.put("records", object);
		return resultMessage(0, _obj.toString());
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONArray array) {
		_obj.put("records", array);
		return resultMessage(0, _obj.toString());
	}

	private String resultMessage(int num) {
		return resultMessage(num, "");
	}

	private String resultMessage(int num, String message) {
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
		default:
			msg = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
