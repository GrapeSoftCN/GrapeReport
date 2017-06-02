package interfaceApplication;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
import database.db;
import esayhelper.DBHelper;
import esayhelper.JSONHelper;
import esayhelper.formHelper;
import esayhelper.jGrapeFW_Message;
import esayhelper.formHelper.formdef;
import nlogger.nlogger;
import session.session;

public class Rtype {
	private static DBHelper type;
	private static formHelper form;
	private JSONObject _obj = new JSONObject();

	static {
		type = new DBHelper(appsProxy.configValue().get("db").toString(), "reportType");
		form = type.getChecker();
	}

	private db bind() {
		return type.bind(String.valueOf(appsProxy.appid()));
	}

	// 新增
	public String AddType(String typeInfo) {
		String info = "";
		form.putRule("TypeName", formdef.notNull);
		JSONObject object = JSONHelper.string2json(typeInfo);
		if (object != null) {
			try {
				if (!form.checkRuleEx(object)) {
					return resultMessage(1);
				}
				if (findByName(object.get("TypeName").toString()) != null) {
					return resultMessage(2);
				}
				info = bind().data(object).insertOnce().toString();
			} catch (Exception e) {
				info = "";
			}
		}
		if (("").equals(info)) {
			resultMessage(99);
		}
		return findById(info);
	}

	// 修改
	public String UpdateType(String id, String typeInfo) {
		int code = 99;
		JSONObject object = JSONHelper.string2json(typeInfo);
		if (object != null) {
			try {
				if (object.containsKey("TypeName")) {
					if (findByName(object.get("TypeName").toString()) != null) {
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
	public String DeleteType(String id) {
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
	public String DeleteBatchType(String ids) {
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
	public String PageType(int ids, int pageSize) {
		JSONObject object = null;
		try {
			JSONArray array = bind().page(ids, pageSize);
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

	// 条件分页
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
				JSONArray array = bind().dirty().page(ids, pageSize);
				object = new JSONObject();
				object.put("totalSize", (int) Math.ceil((double) bind().count() / pageSize));
				object.put("currentPage", ids);
				object.put("pageSize", pageSize);
				object.put("data", array);
			}
			catch(Exception e){
				obj = null;
			}
		}
		return resultMessage(object);
	}

	private JSONObject findByName(String name) {
		return bind().eq("TypeName", name).find();
	}

	public String findById(String id) {
		JSONObject object = new JSONObject();
		session session = new session();
		if (session.get(id) != null) {
			String info = session.get(id).toString();
			object = JSONHelper.string2json(info);
		} else {
			object = bind().eq("_id", new ObjectId(id)).find();
			if (object == null) {
				session.setget(id, "");
			} else {
				session.setget(id, object.toString());
			}
		}
		return object != null ? resultMessage(object) : resultMessage(0, "");
	}

	@SuppressWarnings("unchecked")
	private String resultMessage(JSONObject object) {
		if (object==null) {
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
