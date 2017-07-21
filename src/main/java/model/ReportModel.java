package model;

import java.io.FileInputStream;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import JGrapeSystem.jGrapeFW_Message;
import apps.appsProxy;
import authority.privilige;
import cache.CacheHelper;
import check.checkHelper;
import check.formHelper;
import check.formHelper.formdef;
import database.DBHelper;
import database.db;
import httpClient.request;
import interrupt.interrupt;
import json.JSONHelper;
import nlogger.nlogger;
import rpc.execRequest;
import security.codec;
import session.session;
import sms.ruoyaMASDB;
import string.StringHelper;
import time.TimeHelper;

public class ReportModel {
	private DBHelper report;
	private formHelper form;
	private JSONObject _obj = new JSONObject();
	private int appid = appsProxy.appid();
	private List<String> imgList = new ArrayList<>();
	private List<String> videoList = new ArrayList<>();
	private JSONObject UserInfo = null;
	private String sid = null;
	private session session;
	private CacheHelper cache = new CacheHelper();

	public ReportModel() {
		report = new DBHelper(appsProxy.configValue().getString("db"), "reportInfo");
		form = report.getChecker();
		session = new session();
		UserInfo = new JSONObject();
		sid = (String) execRequest.getChannelValue("sid");
		if (sid != null) {
			UserInfo = session.getSession(sid);
		}
	}

	private db getdb() {
		return getdb(appsProxy.configValue());
	}

	private db getdb(JSONObject threadifo) {
		DBHelper tmpdb = new DBHelper(threadifo.get("db").toString(), "reportInfo", "_id");
		return tmpdb.bind(String.valueOf(appid));
	}

	private db bind() {
		nlogger.logout(appsProxy.appid());
		return report.bind(String.valueOf(appid));
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
				if (object.get("userid") == null) {
					return resultMessage(19);
				}
				userid = object.get("userid").toString();
				if (("").equals(userid)) {
					return resultMessage(15);
				}
				int mode = Integer.parseInt(object.get("mode").toString());
				switch (mode) {
				case 0:
					info = NonAnonymous(userid, object);
					break;
				case 1:
					info = Anonymous(object);
					break;
				}
			} catch (Exception e) {
				nlogger.logout(e);
				info = resultMessage(99);
			}
		}
		return info;
	}

	// 修改
	@SuppressWarnings("unchecked")
	public int Update(String id, JSONObject object) {
		int code = 99;
		int role = getRoleSign();
		if (role == 6) {
			return 20;
		}
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
				nlogger.logout(e);
				code = 99;
			}
		}
		return code;
	}

	// 删除
	public int Delete(String id) {
		int role = getRoleSign();
		if (role == 6) {
			return 20;
		}
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
		int role = getRoleSign();
		if (role == 6) {
			return 20;
		}
		String code = String.valueOf(getdb().eq("state", 3).deleteAll());
		return Integer.parseInt(code);
	}

	// 批量删除
	public int Delete(String[] ids) {
		int code = 99;
		int role = getRoleSign();
		if (role == 6) {
			return 20;
		}
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

	/** 前台分页显示 */
	@SuppressWarnings("unchecked")
	public String page2(String wbid, int ids, int pageSize, String info) {
		db db = getdb();
		JSONObject object = new JSONObject();
		JSONArray array = new JSONArray();
		JSONObject objects = JSONObject.toJSON(info);
		String key;
		try {
			if (objects != null) {
				for (Object obj : objects.keySet()) {
					key = obj.toString();
					db.eq(key, objects.get(key));
				}
				db.eq("wbid", wbid);
				array = db.dirty().page(ids, pageSize);
				JSONArray array2 = dencode(array);
				object.put("data", getImg(array2));
				object.put("totalSize", (int) Math.ceil((double) db.count() / pageSize));
			} else {
				object.put("totalSize", 0);
				object.put("data", array);
			}
		} catch (Exception e) {
			nlogger.logout(e);
			object.put("totalSize", 0);
			object.put("data", array);
		} finally {
			db.clear();
		}
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		return resultMessage(object);
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String page(int ids, int pageSize) {
		db db = getdb();
		JSONObject object = new JSONObject();
		JSONArray array = new JSONArray();
		int roleSign = getRoleSign();
		try {
			// 获取角色权限
			if (roleSign == 6 || roleSign == 5 || roleSign == 4) {
				db.desc("time");
			} else if (roleSign == 3 || roleSign == 2) {
				db.eq("wbid", (String) UserInfo.get("currentWeb")).desc("time");
			} else {
				db.eq("slevel", 0).desc("time");
			}
			array = db.page(ids, pageSize);
			JSONArray array2 = dencode(array);
			object.put("totalSize", (int) Math.ceil((double) db.count() / pageSize));
			object.put("data", getImg(array2));
		} catch (Exception e) {
			nlogger.logout(e);
			object.put("totalSize", 0);
			object.put("data", array);
		}
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		return resultMessage(object);
	}

	// 条件分页
	@SuppressWarnings("unchecked")
	public String page(int ids, int pageSize, JSONObject objects) {
		int roleSign = getRoleSign();
		db db = getdb();
		JSONObject object = new JSONObject();
		JSONArray array = new JSONArray();
		try {
			if (objects != null) {
				for (Object obj : objects.keySet()) {
					if (obj.equals("_id")) {
						db.eq("_id", new ObjectId(objects.get("_id").toString()));
					} else {
						db.eq(obj.toString(), objects.get(obj.toString()));
					}
				}
				if (roleSign == 6 ||roleSign == 5 || roleSign == 4) {
					db.desc("time");
				} else if (roleSign == 3 || roleSign == 2) {
					db.eq("wbid", (String) UserInfo.get("currentWeb")).desc("time");
				} else {
					db.eq("slevel", 0).desc("time");
				}
				array = db.dirty().page(ids, pageSize);
				JSONArray array2 = dencode(array);
				object.put("data", getImg(array2));
				object.put("totalSize", (int) Math.ceil((double) db.count() / pageSize));
			} else {
				object.put("totalSize", 0);
				object.put("data", array);
			}
		} catch (Exception e) {
			nlogger.logout(e);
			object.put("totalSize", 0);
			object.put("data", array);
		} finally {
			db.clear();
		}
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		return resultMessage(object);
	}

	@SuppressWarnings("unchecked")
	public String PageBy(int idx, int pageSize, String info) {
		db db = bind();
		int role = getRoleSign();
		JSONObject rs = new JSONObject();
		JSONArray conds = JSONHelper.string2array(info);
		JSONArray data = null;
		db = db.where(conds);
		switch (role) {
		case 6:
		case 5: // 管理员
		case 4:
			break;
		case 3:
		case 2:
		case 1:
			String curweb = (String) UserInfo.get("currentWeb");
			if (curweb != null) {
				String webTree = (String) appsProxy.proxyCall(callHost(), "13/17/WebInfo/getWebTree/" + curweb, null,
						null);
				String[] webtree = webTree.split(",");
				int i;
				int l = webtree.length;
				db.or();
				for (i = 0; i < l; i++) {
					if (!webtree[i].equals(curweb)) {
						db.eq("wbid", webtree[i]);
					}
				}
			} else {
				db.eq("wbid", "");
			}
			break;
		default:
			db.eq("slevel", 0);
			break;
		}
		data = db.dirty().desc("time").page(idx, pageSize);
		if (data != null) {
			int l = (int) Math.ceil((double) db.count() / pageSize);
			rs.put("totalSize", l);
			rs.put("pageSize", pageSize);
			rs.put("currentPage", idx);
			rs.put("data", getImg(dencode(data)));
		}
		db.clear();
		return resultMessage(rs);
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
		if (obj != null) {
			if (obj.containsKey("content") && obj.get("content") != "") {
				obj.put("content", codec.decodebase64(obj.get("content").toString()));
			}
			if (obj.containsKey("reason") && obj.get("reason") != "") {
				obj.put("reason", codec.decodebase64(obj.get("reason").toString()));
			}
		}
		return obj;
	}

	// 模糊查询
	@SuppressWarnings("unchecked")
	public String find(int ids, int pageSize, JSONObject objects) {
		JSONObject object = null;
		if (objects != null) {
			try {
				db db = getdb().or();
				for (Object obj : objects.keySet()) {
					db.like(obj.toString(), objects.get(obj.toString()));
				}
				JSONArray array = db.dirty().page(ids, pageSize);
				JSONArray array2 = dencode(array);
				object = new JSONObject();
				object.put("totalSize", (int) Math.ceil((double) getdb().count() / pageSize));
				object.put("pageSize", pageSize);
				object.put("currentPage", ids);
				object.put("data", getImg(array2));
			} catch (Exception e) {
				nlogger.logout(e);
				object = null;
			} finally {
				getdb().clear();
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
				nlogger.logout(e);
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
		db db = getdb();
		JSONArray array = null;
		try {
			array = new JSONArray();
			if (object == null) {
				db.eq("state", 1L);
			} else {
				for (Object obj : object.keySet()) {
					if (obj.equals("_id")) {
						db.eq("_id", new ObjectId(object.get("_id").toString()));
					}
					if (obj.equals("state")) {
						db.eq(obj.toString(), Long.parseLong(object.get(obj.toString()).toString()));
					} else {
						db.eq(obj.toString(), object.get(obj.toString()).toString());
					}
				}
			}
			array = db.limit(50).select();
		} catch (Exception e) {
			nlogger.logout(e);
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
		int role = getRoleSign();
		if (role == 6) {
			return 20;
		}
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
				nlogger.logout(e);
				code = 99;
			}
		}
		return code;
	}

	// 举报拒绝
	@SuppressWarnings("unchecked")
	public int Refuse(String id, JSONObject reasons) {
		int code = 99;
		int role = getRoleSign();
		if (role == 6) {
			return 20;
		}
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
				nlogger.logout(e);
				code = 99;
			}
		}
		return code;
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
			nlogger.logout(e);
			array = null;
		}
		JSONArray array2 = getImg(array);
		return resultMessage(dencode(array2));
	}

	public String searchs(String wbid, String userid, int no) {
		JSONArray array = null;
		try {
			array = new JSONArray();
			array = getdb().eq("wbid", wbid).eq("userid", userid).mask("content,reason").limit(no).select();
		} catch (Exception e) {
			nlogger.logout(e);
			array = null;
		}
		JSONArray array2 = getImg(array);
		return resultMessage(dencode(array2));
	}

	public String searchReport(String wbid, String userid, int no) {
		db db = getdb();
		if (UserInfo != null && UserInfo.size() != 0) {
			wbid = UserInfo.get("currentWeb").toString();
		}
		db.eq("wbid", wbid);
		JSONArray array = new JSONArray();
		try {
			array = db.eq("userid", userid).field("_id,content,time").limit(no).desc("time").select();
		} catch (Exception e) {
			nlogger.logout(e);
			array = new JSONArray();
		}
		JSONArray array2 = getImg(array);
		return resultMessage(dencode(array2));
	}

	public String counts(String userid) {
		String count = "";
		try {
			JSONArray array = bind().count("userid").group("userid");
			JSONObject obj = new JSONObject();
			for (int i = 0; i < array.size(); i++) {
				obj = (JSONObject) array.get(i);
				if (obj.get("_id") == null) {
					continue;
				} else if (!userid.equals(obj.get("_id").toString())) {
					continue;
				} else {
					count = String.valueOf(obj.get("count").toString());
					break;
				}
			}
		} catch (Exception e) {
			nlogger.logout(e);
			count = "";
		}
		// return resultMessage(0, String.valueOf(count));
		return resultMessage(0, ("").equals(count) ? "0" : count);
	}

	// 统计已受理举报数量
	public String counts() {
		// long count = getdb().eq("state", 0).count();
		db db = getdb();
		long count = 0;
		int roleSign = getRoleSign();
		if (UserInfo == null) {
			return resultMessage(20);
		}
		try { // 获取角色权限
			if (roleSign == 3 || roleSign == 2) {
				db.eq("wbid", UserInfo.get("currentWeb").toString());
			} else if (roleSign == 1 || roleSign == 0) {
				db.eq("slevel", 0);
			}
			count = db.eq("state", 0).count();
		} catch (Exception e) {
			nlogger.logout(e);
			count = 0;
		}

		// int count = 0;
		// JSONArray array = getdb().count("state").group("state");
		// JSONObject obj = new JSONObject();
		// for (int i = 0; i < array.size(); i++) {
		// obj = (JSONObject) array.get(i);
		// String value = obj.get("_id").toString();
		// if (value.contains("$numberLong")) {
		// value = JSONHelper.string2json(value).get("$numberLong").toString();
		// }
		// if (Integer.parseInt(value) != 0) {
		// continue;
		// }
		// count = Integer.parseInt(obj.get("count").toString());
		// }
		// return resultMessage(0, String.valueOf(count));
		return resultMessage(0, String.valueOf(count));
	}

	public String feed(String userid) {
		long count = getdb().eq("userid", userid).ne("state", 0).count();
		return resultMessage(0, String.valueOf(count));
	}

	// 导出举报件
	/*
	 * public String print(String info) { String path = ""; String Date =
	 * TimeHelper.stampToDate(TimeHelper.nowMillis()).split(" ")[0]; String uuid
	 * = UUID.randomUUID().toString(); try { byte[] by =
	 * excelHelper.out("GrapeReport/Report/excel/" + info); if (by != null) {
	 * String fileurl = "C://JavaCode/tomcat/webapps/File/upload/" + Date +
	 * "/wechat"; path = fileurl + "/" + uuid + ".excel"; if
	 * (fileHelper.createFileEx(path)) { FileOutputStream fos = new
	 * FileOutputStream(path); fos.write(by); fos.close(); } } } catch
	 * (Exception e) { nlogger.logout(e); return resultMessage(99); } return
	 * resultMessage(0, path); // if (file == null) { // return resultMessage(0,
	 * "没有符合条件的数据"); // } // String uuid = UUID.randomUUID().toString(); // File
	 * tarFile = new File("\file\\tomcat\\webapps\\File\\upload\\" + // Date +
	 * "\\Grape" + uuid + ".xls"); // file.renameTo(tarFile); // String target =
	 * tarFile.toString(); // String hoString = "http://"; // target = hoString
	 * + getAppIp("file").split("/")[1] + // target.split("webapps")[1]; //
	 * return resultMessage(0, target); }
	 */
	public String print(String info) {
		String path = "";
		try {
			String host = "http://" + FileHost(0);
			// String host = "http://localhost:8080";
			String DataInfo = excel(info);
			DataInfo = URLEncoder.encode(DataInfo, "UTF-8");
			path = request.Get(host + "/File/ExportReport?info=" + DataInfo);
			path = host + getimage(path);
		} catch (Exception e) {
			nlogger.logout(e);
		}

		return resultMessage(0, path);
	}

	@SuppressWarnings("unchecked")
	private String excel(String info) {
		JSONObject objs = JSONHelper.string2json(info);
		JSONObject object = null;
		if (objs != null) {
			try {
				object = new JSONObject();
				JSONArray array = findexcel(objs);
				for (int i = 0; i < array.size(); i++) {
					object = (JSONObject) array.get(i);
					for (Object obj : object.keySet()) {
						String value = object.get(obj.toString()).toString();
						if (value.contains("$numberLong")) {
							JSONObject object2 = JSONHelper.string2json(value);
							object.put(obj.toString(), Long.parseLong(object2.get("$numberLong").toString()));
						}
					}
				}
			} catch (Exception e) {
				nlogger.logout(e);
				object = null;
			}
		}
		return object != null ? object.toString() : "";
	}

	// 获取文件相对路径
	private String getimage(String imageURL) {
		int i = 0;
		if (imageURL.contains("File//upload")) {
			i = imageURL.toLowerCase().indexOf("file//upload");
		}
		if (imageURL.contains("File\\upload")) {
			i = imageURL.toLowerCase().indexOf("file\\upload");
		}
		imageURL = imageURL.substring(i);
		return "\\" + imageURL;
	}

	// 显示举报信息
	public String SearchById(String id) {
		JSONObject object = bind().eq("_id", new ObjectId(id)).find();
		return resultMessage(getImg(dencode(object)));
	}

	public String insert(JSONObject info) {
		String tip = null;
		if (info != null) {
			try {
				tip = getdb().data(info).insertOnce().toString();
			} catch (Exception e) {
				nlogger.logout(e);
				tip = null;
			}
		}
		return tip != null ? tip : "";
	}

	// 实名举报
	public String RealName(JSONObject object) {
		int code = 99;
		String openid = object.get("userid").toString();
		JSONObject object2 = FindByopenid(openid);
		if (object2 != null && object2.containsKey("openid")) {
			String phone = object2.get("phone").toString();
			String ckcode = getValidateCode();
			// 1.发送验证码
			code = SendVerity(phone, "验证码:" + ckcode);
			if (code == 0) {
				String nextstep = appid + "/45/Report/insert/" + object.toString();
				JSONObject object3 = interrupt._exist(phone, String.valueOf(appid));
				if (object3 != null) {
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
	@SuppressWarnings({ "unchecked" })
	public String resu(JSONObject object) {
		String openid = object.get("openid").toString();
		// 判断是否实名认证
		JSONObject object2 = FindByopenid(openid);
		if (object2 != null && object2.size() != 0) {
			if (("").equals(object2.get("headimgurl").toString())) {
				object2 = getHeadImgUrl(openid, object2);
			}
		}
		if (object2 != null && object2.containsKey("openid")) {
			if (!object.containsKey("phone")) {
				String phone = object2.get("phone").toString();
				object.put("phone", phone);
			}
		}
		if (object.containsKey("ckcode") && object.containsKey("phone")) {
			int code = interrupt._resume(object.get("ckcode").toString(), object.get("phone").toString(),
					String.valueOf(appsProxy.appid()));
			if (code == 0) {
				return resultMessage(4);
			}
			if (code == 1) {
				return resultMessage(5);
			}
			if (object.containsKey("type")) {
				if (("2").equals(object.get("type").toString())) {
					String url = appid + "/45/Report/insert/s:" + session.get(object.get("openid").toString());
					appsProxy.proxyCall(callHost(), url, null, "").toString();
					// String message = appsProxy.proxyCall(host,
					// "/15/wechatUser/FindById/"
					// + object.get("openid").toString(),
					// null, "").toString();
					// return message;
				}
			}
			// message = appsProxy.proxyCall(callHost(), appid +
			// "/16/wechatUser/FindOpenId/" + openid, null, "")
			// .toString();
		}
		// nlogger.logout(" resume message" + message);
		return resultMessage(object2);
	}

	// // 验证内容是否含有敏感字符串
	// public int checkCont(String content) {
	// if (WordFilter.isContains(content)) {
	// return 3;
	// }
	// return 0;
	// }

	// 获取用户openid，实名认证
	@SuppressWarnings("unchecked")
	public String getId(String code, String url) {
		JSONObject object = new JSONObject();
		String msg = jGrapeFW_Message.netMSG(1, object.toString());
		String openid = "";
		try {
			if (code != null && !code.equals("")) {
				// 获取微信签名
				String signMsg = appsProxy.proxyCall(callHost(), appid + "/30/Wechat/getSignature/" + url, null, "")
						.toString();
				nlogger.logout(signMsg);
				String sign = JSONHelper.string2json(signMsg).get("message").toString();
				openid = appsProxy.proxyCall(callHost(), appid + "/30/Wechat/BindWechat/" + code, null, "").toString();
				if (openid.equals("")) {
					return jGrapeFW_Message.netMSG(7, "code错误");
				}
				JSONObject object2 = FindByopenid(openid);
				object2 = getHeadImgUrl(openid, object2);
				nlogger.logout(object2);
				if (object2 != null && object2.containsKey("openid")) {
					object.put("msg", "已实名认证");
					object.put("openid", openid);
					object.put("headimgurl", object2.get("headimgurl").toString());
					object.put("sign", sign);
					msg = jGrapeFW_Message.netMSG(0, object.toString());
				} else {
					object.put("msg", "未实名认证");
					object.put("openid", openid);
					object.put("sign", sign);
					msg = jGrapeFW_Message.netMSG(1, object.toString());
				}
			}
		} catch (Exception e) {
			nlogger.logout(e);
			object.put("tips", "接口使用次数已达上限");
			object.put("openid", openid);
			msg = jGrapeFW_Message.netMSG(2, object.toString());
		}
		return msg;
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
			// interrupt._breakMust(chk, uniqueName, nextStep, appid)
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
		int role = getRoleSign();
		if (role == 6) {
			return resultMessage(20);
		}
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
		int role = getRoleSign();
		if (role == 6) {
			return resultMessage(20);
		}
		return appsProxy.proxyCall(callHost(), appid + "/16/wechatUser/unkick/", null, "").toString();
	}

	// // 举报量统计
	// public String EventCounts(JSONObject object) {
	// db db = setCondString(getdb().and(), object);
	// return resultMessage(0, String.valueOf(db.count()));
	// }

	public String EventCounts(String param) {
		JSONArray array = JSONArray.toJSONArray(param);
		return resultMessage(0, String.valueOf(getdb().where(array).count()));
	}

	// 举报量与全量比例
	// public String PercentCounts(JSONObject object) {
	// float count = (float) getdb().count();
	// db db = setCondString(getdb().and(), object);
	// float Eventcount = (float) db.count();
	// return resultMessage(0, String.format("%.2f", (double) (Eventcount /
	// count)));
	// }
	public String PercentCounts(String param) {
		float count = (float) getdb().count(); // 总量
		float Eventcount = (float) Long.parseLong(EventCounts(param));
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
		db tmpdb = getdb(threadInfo);
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

	public int Selvel(String id) {
		String[] value = getId(id);
		int code = 99;
		String condString = "{\"slevel\":0}";
		try {
			if (value.length == 1) {
				code = bind().eq("_id", new ObjectId(id)).data(condString).update() != null ? 0 : 99;
			} else {
				db db = bind().or();
				for (String _id : value) {
					db.eq("_id", new ObjectId(_id));
				}
				code = db.data(condString).updateAll() == value.length ? 0 : 99;
			}
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return code;
	}

	private String getSubWeb(String webinfo) {
		JSONObject object;
		String records;
		List<String> list = new ArrayList<String>();
		object = JSONHelper.string2json(webinfo);
		if (object != null) {
			object = (JSONObject) object.get("message");
			if (object != null) {
				JSONObject objID;
				records = object.get("records").toString();
				JSONArray array = JSONHelper.string2array(records);
				for (int i = 0; i < array.size(); i++) {
					object = (JSONObject) array.get(i);
					objID = (JSONObject) object.get("_id");
					list.add(objID.get("$oid").toString());
				}
			}
		}
		return StringHelper.join(list);
	}

	private String[] getId(String id) {
		List<String> list = new ArrayList<>();
		JSONObject object;
		String[] value = id.split(",");
		for (String _id : value) {
			object = bind().eq("_id", new ObjectId(_id)).field("slevel").find();
			if (object != null) {
				String slevel = object.get("slevel").toString();
				if (slevel.contains("$numberLong")) {
					slevel = JSONHelper.string2json(slevel).get("$numberLong").toString();
				}
				if (!("0").equals(slevel)) {
					list.add(_id);
				}
			}
		}
		return StringHelper.join(list).split(",");
	}

	/**
	 * 根据角色plv，获取角色级别
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @return
	 *
	 */
	private int getRoleSign() {
		int roleSign = 0; // 游客
		if (sid != null) {
			try {
				privilige privil = new privilige(sid);
				int roleplv = privil.getRolePV(appsProxy.appidString());
				if (roleplv >= 1000 && roleplv < 3000) {
					roleSign = 1; // 普通用户即企业员工
				}
				if (roleplv >= 3000 && roleplv < 5000) {
					roleSign = 2; // 栏目管理员
				}
				if (roleplv >= 5000 && roleplv < 8000) {
					roleSign = 3; // 企业管理员
				}
				if (roleplv >= 8000 && roleplv < 10000) {
					roleSign = 4; // 监督管理员
				}
				if (roleplv >= 10000 && roleplv < 12000) {
					roleSign = 5; // 总管理员
				}
				if (roleplv >= 12000) {
					roleSign = 6; // 表示纪委用户，只有查看权限
				}
			} catch (Exception e) {
				nlogger.logout(e);
				roleSign = 0;
			}
		}
		return roleSign;
	}

	private String FileHost(int signal) {
		return getAppIp("file").split("/")[signal];
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
			nlogger.logout(e);
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

	/*
	 * private JSONObject find(String time, String logic) { if
	 * (time.contains("$numberLong")) { JSONObject object =
	 * JSONHelper.string2json(time); time =
	 * object.get("$numberLong").toString(); } if (logic == "<") {
	 * getdb().lt("time", time).desc("time"); } else { getdb().gt("time",
	 * time).asc("time"); } return getdb().find(); }
	 */

	private JSONArray getImg(JSONArray array) {
		JSONObject object;
		String fid = "";
		if (array != null) {
			try {
				if (array.size() == 0) {
					return array;
				}
				int l = array.size();
				for (int i = 0, len = l; i < len; i++) {
					object = (JSONObject) array.get(i);
					if (object.containsKey("attr") && !("").equals(object.get("attr").toString())) {
						fid += object.get("attr").toString() + ",";
					}
				}
				if (fid.length() > 1) {
					fid = StringHelper.fixString(fid, ',');
				}
				if (!fid.equals("")) {
					array = getMediaInfo(array, fid);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return array;
	}

	@SuppressWarnings("unchecked")
	private JSONArray getMediaInfo(JSONArray array, String fid) {
		List<String> liStrings = new ArrayList<>();
		imgList = new ArrayList<>();
		videoList = new ArrayList<>();
		int l = array.size();
		JSONObject object;
		String fileInfo = appsProxy.proxyCall(outerHost(), appsProxy.appid() + "/24/Files/getFiles/" + fid, null, "")
				.toString();
		JSONObject fileObj = JSONObject.toJSON(fileInfo);
		JSONObject FileInfoObj;
		if (fileObj != null) {
			String[] attr;
			for (int i = 0, len = l; i < len; i++) {
				object = (JSONObject) array.get(i);
				attr = object.getString("attr").split(",");
				int attrlength = attr.length;
				if (attrlength != 0 && !attr[0].equals("") || attrlength > 1) {
					for (int j = 0; j < attrlength; j++) {
						FileInfoObj = (JSONObject) fileObj.get(attr[j]);
						FileInfoObj.remove("_id");
						object.put("attrFile" + j, FileInfoObj);
						if ("1".equals(FileInfoObj.get("filetype").toString())) {
							imgList.add("http://" + getAppIp("file").split("/")[1]
									+ FileInfoObj.get("filepath").toString());
						}
						if ("2".equals(FileInfoObj.get("filetype").toString())) {
							videoList.add("http://" + getAppIp("file").split("/")[1]
									+ FileInfoObj.get("filepath").toString());
						}
					}
				}
				if (object.containsKey("mediaid")) {
					liStrings = getMedia(liStrings, object.get("mediaid").toString());
				}
				object.put("image", imgList.size() != 0 ? StringHelper.join(imgList) : "");
				object.put("video", videoList.size() != 0 ? StringHelper.join(videoList) : "");
				object.put("media", liStrings.size() != 0 ? StringHelper.join(liStrings) : "");
				if (object.containsKey("type")) {
					object = getType(object);
				}
				array.set(i, object);
			}
		}
		return array;
	}

	@SuppressWarnings("unchecked")
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
				nlogger.logout(e);
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
			Object type = object.get("type");
			if (type == null) {
				object.put("ReportType", "测试数据");
			} else if (("0").equals(type.toString()) || type.toString().length() < 24) {
				object.put("ReportType", "");
			} else {
				if (session.get(type.toString()) != null) {
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
		String imgurl = "http://" + getAppIp("file").split("/")[1];
		String temp;
		CacheHelper cache = new CacheHelper();
		if (object != null && !("").equals(object)) {
			if (!("").equals(imgid)) {
				String fileInfo = "";
				if (cache.get(imgid) != null) {
					fileInfo = cache.get(imgid).toString();
				} else {
					// 获取文件对象getAppIp("file")
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
					if ("1".equals(object2.get("filetype").toString())) {
						imgList.add(imgurl + object2.get("filepath").toString());
					}
					if ("2".equals(object2.get("filetype").toString())) {
						videoList.add(imgurl + object2.get("filepath").toString());
					}
					if (object2.containsKey("ThumbnailImage")) {
						temp = imgurl + object2.get("ThumbnailImage").toString();
						object2.put("ThumbnailImage", temp);
					}
					if (object2.containsKey("ThumbnailVideo")) {
						temp = imgurl + object2.get("ThumbnailVideo").toString();
						object2.put("ThumbnailVideo", temp);
					}
					object.put(key, object2);
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
		try {
			JSONObject object2 = FindByopenid(userid);
			if (object2 == null || object2.size() == 0) {
				cache.setget(object.get("userid").toString(), object.toString());
				return resultMessage(12);
			}
			if (("1").equals(object2.get("isdelete").toString())) {
				info = resultMessage(9);
			}
			info = RealName(object);
		} catch (Exception e) {
			nlogger.logout(e);
			info = resultMessage(99);
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

	// 获取
	private JSONObject FindByopenid(String openid) {
		String info = "";

		if (cache.get(openid + "Info") != null) {
			info = cache.get(openid + "Info").toString();
		} else {
			info = appsProxy.proxyCall(callHost(), appid + "/16/wechatUser/FindOpenId/" + openid, null, "").toString();
			cache.setget(openid + "Info", info);
		}
		JSONObject object2 = JSONHelper.string2json(info);
		object2 = JSONHelper.string2json(object2.get("message").toString());
		if (object2 != null) {
			object2 = JSONHelper.string2json(object2.get("records").toString());
		}
		return object2;
	}

	@SuppressWarnings("unchecked")
	private JSONObject getHeadImgUrl(String openid, JSONObject objs) {
		JSONObject obj = null;
		try {
			JSONObject object = getHeadurl(openid);
			String newimg = object.getString("headimgurl");
			String oldimg = objs != null ? (String) objs.get("headimgurl") : "";
			if (oldimg.equals("") || !oldimg.equals(newimg)) {
				newimg = codec.encodebase64(newimg);
				if (newimg.contains("+")) {
					newimg.replaceAll("+", "@w");
				}
				if (newimg.contains("=")) {
					newimg.replaceAll("=", "@m");
				}
				object.put("headimgurl", newimg);
				appsProxy.proxyCall(callHost(),
						appsProxy.appid() + "/16/wechatUser/UpdateInfo/s:" + openid + "/s:" + object.toString(), null,
						"").toString();
			}
			obj = FindByopenid(openid);
		} catch (Exception e) {
			nlogger.logout(e);
			obj = null;
		}
		return obj;
	}

	// 根据微信id获取微信头像
	private JSONObject getHeadurl(String openid) {
		JSONObject object = new JSONObject();
		try {
			String userinfo = appsProxy
					.proxyCall(callHost(), appsProxy.appid() + "/30/Wechat/getUserInfo/s:" + openid, null, "")
					.toString();
			if (JSONHelper.string2json(userinfo) != null) {
				String message = JSONHelper.string2json(userinfo).get("message").toString();
				String records = JSONHelper.string2json(message).get("records").toString();
				String headimgurl = "";
				if (records.contains("headimgurl")) {
					headimgurl = JSONHelper.string2json(records).get("headimgurl").toString();
				}
				object.put("headimgurl", headimgurl);
			}
		} catch (Exception e) {
			nlogger.logout(e);
		}
		return object;
	}

	// 优先级加1
	public int getPriority(String id) {
		int priority = 0;
		JSONObject object = bind().eq("_id", new ObjectId(id)).field("priority").find();
		if (object != null) {
			String pString = object.get("priority").toString();
			if (pString.contains("$numberLong")) {
				pString = JSONHelper.string2json(pString).get("$numberLong").toString();
			}
			priority = Integer.parseInt(pString) + 1;
		}
		return priority;
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
					Map.Entry<String, Object> entry = iterator.next();
					if (!object.containsKey(entry.getKey())) {
						object.put(entry.getKey(), entry.getValue());
					}
				}
			}
		}
		return object;
	}

	public String AddWebReport(JSONObject object) {
		String result = resultMessage(99);
		try {
			String content = (String) object.get("content");
			content = codec.DecodeHtmlTag(content);
			content = codec.decodebase64(content);
			// String key = appsProxy
			// .proxyCall(callHost(), appsProxy.appid() +
			// "/106/KeyWords/CheckKeyWords/" + content, null, "")
			// .toString();
			// JSONObject keywords = JSONHelper.string2json(key);
			// long codes = (Long) keywords.get("errorcode");
			// if (codes == 3) {
			// return resultMessage(3);
			// }
			object.put("content", content);
			result = add(object);
		} catch (Exception e) {
			nlogger.logout(e);
			result = resultMessage(99);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private String add(JSONObject object) {
		String result = resultMessage(99);
		int mode = Integer.parseInt(object.get("mode").toString());
		try {
			if (mode == 1) {
				// 实名验证,发送短信验证码
				result = Real(object);
			} else {
				object.put("content", codec.encodebase64(object.get("content").toString()));
				result = insert(object.toString());
			}
		} catch (Exception e) {
			nlogger.logout(e);
			result = resultMessage(99);
		}
		return result;
	}

	/**
	 * 实名认证
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param object
	 *            咨询建议信息
	 * @param obj
	 *            用户信息
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private String Real(JSONObject object) {
		int code = 0;
		// 发送验证码
		String ckcode = getValidateCode();
		nlogger.logout("验证码：" + ckcode);
		try {
			String phone = UserInfo.get("mobphone").toString();
			if (SendVerity(phone, "验证码:" + ckcode) == 0) {
				object.put("content", codec.encodebase64(object.get("content").toString())); // 对举报内容进行编码
				String nextstep = appsProxy.appid() + "/45/Report/insert/" + object.toString();
				JSONObject object2 = interrupt._exist(phone, String.valueOf(appsProxy.appid()));
				if (object2 != null) {
					code = interrupt._clear(phone, String.valueOf(appsProxy.appid())) == true ? 0 : 99;
				}
				if (code == 0) {
					boolean flag = interrupt._breakMust(ckcode, phone, nextstep, String.valueOf(appsProxy.appid()));
					code = flag ? 0 : 99;
				}
			}
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return resultMessage(code, "验证码发送成功");
	}

	/**
	 * 验证用户提交的验证码
	 * 
	 * @project GrapeSuggest
	 * @package interfaceApplication
	 * @file Suggest.java
	 * 
	 * @param ckcode
	 * @param IDCard
	 * @return
	 *
	 */
	public String Resume(String ckcode, String IDCard) {
		String string = resultMessage(99);
		if (UserInfo != null) {
			try {
				if (IDCard.equals(UserInfo.get("IDCard").toString())) {
					int code = interrupt._resume(ckcode, UserInfo.get("mobphone").toString(),
							String.valueOf(appsProxy.appid()));
					switch (code) {
					case 0:
						string = resultMessage(5);
						break;
					case 1:
						string = resultMessage(6);
						break;
					}
					string = resultMessage(0, "举报信息提交成功");
				}
			} catch (Exception e) {
				nlogger.logout(e);
				string = resultMessage(99);
			}
		}
		return string;
	}

	public String insert(String info) {
		int code = 99;
		try {
			code = getdb().data(info).insertOnce() != null ? 0 : 99;
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return resultMessage(code, "提交成功");
	}

	public JSONObject check(JSONObject obj, HashMap<String, Object> map) {
		JSONObject object = AddMap(map, obj);
		return !form.checkRuleEx(object) ? null : object;
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
		case 19:
			msg = "服务次数已达到上限,获取用户信息异常，请稍后再试";
			break;
		case 20:
			msg = "没有该操作权限";
			break;
		default:
			msg = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
