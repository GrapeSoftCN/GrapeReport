package interfaceApplication;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import JGrapeSystem.jGrapeFW_Message;
import apps.appsProxy;
import database.db;
import json.JSONHelper;
import database.DBHelper;

public class ReportLog {
	private static DBHelper log;
	private JSONObject _obj = new JSONObject();
	static {
		log = new DBHelper(appsProxy.configValue().get("db").toString(),
				"reportLog");
	}

	private db bind() {
		return log.bind(appsProxy.appid() + "");
	}

	public String Addlog(String info) {
		int code = bind().data(JSONHelper.string2json(info))
				.insertOnce() != null ? 0 : 99;
		return resultMessage(code);
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String Pagelog(int ids, int pageSize) {
		JSONArray array = bind().page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", array);
		return resultMessage(object);
	}

	// 条件分页
	@SuppressWarnings("unchecked")
	public String PageBylog(int ids, int pageSize, String info) {
		bind().and();
		JSONObject objs = JSONHelper.string2json(info);
		for (Object obj : objs.keySet()) {
			bind().eq(obj.toString(), objs.get(obj).toString());
		}
		JSONArray array = bind().dirty().page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", array);
		bind().clear();
		return resultMessage(object);
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONObject object) {
		_obj.put("records", object);
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
			msg = "必填字段为空";
			break;
		case 2:
			msg = "该举报类型已存在";
			break;
		default:
			msg = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
