package interfaceApplication;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import JGrapeSystem.jGrapeFW_Message;
import apps.appsProxy;
import check.formHelper;
import check.formHelper.formdef;
import database.DBHelper;
import database.db;
import json.JSONHelper;
import nlogger.nlogger;

/**
 * 举报拒绝/完结事由管理
 * 
 *
 */
public class Reason {
	private static DBHelper Reason;
	private static formHelper form;
	private JSONObject _obj = new JSONObject();

	static {
		Reason = new DBHelper(appsProxy.configValue().get("db").toString(), "reportReson");
		form = Reason.getChecker();
	}

	private db bind() {
		return Reason.bind(String.valueOf(appsProxy.appid()));
	}

	// 新增
	@SuppressWarnings("unchecked")
	public String AddReson(String info) {
		String code = "";
		form.putRule("Rcontent", formdef.notNull);
		JSONObject object = JSONHelper.string2json(info);
		if (object != null) {
			try {
				object.put("count", 0);
				if (!form.checkRuleEx(object)) {
					return resultMessage(1);
				}
				if (findByName(object.get("Rcontent").toString()) != null) {
					return resultMessage(2);
				}
				code = bind().data(object).insertOnce().toString();
			} catch (Exception e) {
				code = "";
			}
		}
		if (("").equals(code)) {
			resultMessage(99);
		}
		return resultMessage(findById(code));
	}

	// 修改
	public String UpdateReson(String id, String Info) {
		int code = 99;
		JSONObject object = JSONHelper.string2json(Info);
		if (object != null) {
			try {
				if (object.containsKey("Rcontent")) {
					if (findByName(object.get("Rcontent").toString()) != null) {
						return resultMessage(2);
					}
				}
				code = bind().eq("_id", new ObjectId(id)).data(object).update() != null ? 0 : 99;
			} catch (Exception e) {
				code = 99;
			}
		}
		return resultMessage(code, "修改成功");
	}

	// 删除
	public String DeleteReson(String id) {
		int code = 99;
		try {
			JSONObject object = bind().eq("_id", new ObjectId(id)).delete();
			code = (object != null ? 0 : 99);
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return resultMessage(code, "删除成功");
	}

	// 批量删除
	public String DeleteBatchReson(String ids) {
		int code = 99;
		try {
			String[] value = ids.split(",");
			int len = value.length;
			bind().or();
			for (int i = 0; i < len; i++) {
				bind().eq("_id", new ObjectId(value[i]));
			}
			long codes = bind().deleteAll();
			code = (Integer.parseInt(String.valueOf(codes)) == len ? 0 : 99);
		} catch (Exception e) {
			nlogger.logout(e);
			code = 99;
		}
		return resultMessage(code, "删除成功");
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String PageReson(int ids, int pageSize) {
		JSONObject object = null;
		try {
			JSONArray array = bind().desc("count").page(ids, pageSize);
			object = new JSONObject();
			object.put("totalSize", (int) Math.ceil((double) bind().count() / pageSize));
			object.put("currentPage", ids);
			object.put("pageSize", pageSize);
			object.put("data", array);
		} catch (Exception e) {
			nlogger.logout(e);
			object = null;
		}
		return resultMessage(object);
	}

	// 搜索
	@SuppressWarnings("unchecked")
	public String search(int ids, int pageSize, String info) {
		JSONObject object = null;
		JSONObject obj = JSONHelper.string2json(info);
		if (info != null) {
			try {
				bind().and();
				for (Object object2 : obj.keySet()) {
					if ("_id".equals(object2.toString())) {
						bind().eq("_id", new ObjectId(obj.get("_id").toString()));
					}
					bind().like(object2.toString(), obj.get(object2.toString()));
				}
				JSONArray array = bind().dirty().desc("count").page(ids, pageSize);
				object = new JSONObject();
				object.put("totalSize", (int) Math.ceil((double) bind().count() / pageSize));
				object.put("currentPage", ids);
				object.put("pageSize", pageSize);
				object.put("data", array);
				bind().clear();
			} catch (Exception e) {
				object = null;
			}
		}
		return resultMessage(object);
	}

	// 事由使用次数+1
	@SuppressWarnings("unchecked")
	public String addTime(String name) {
		bind().eq("Rcontent", name);
		JSONObject object = bind().dirty().find();
		if (object != null) {
			object.put("count", Integer.parseInt(object.get("count").toString()) + 1);
			return bind().data(object).update() != null ? resultMessage(0) : resultMessage(99);
		}
		bind().clear();
		return resultMessage(99);
	}

	private JSONObject findByName(String name) {
		JSONObject object = bind().eq("Rcontent", name).find();
		return object != null ? object : null;
	}

	private JSONObject findById(String id) {
		JSONObject object = bind().eq("_id", new ObjectId(id)).find();
		return object != null ? object : null;
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONObject object) {
		if (object == null) {
			object = new JSONObject();
		}
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
