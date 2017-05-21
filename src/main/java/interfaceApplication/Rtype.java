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

public class Rtype {
	private static DBHelper type;
	private static formHelper form;
	private JSONObject _obj = new JSONObject();

	static {
		type = new DBHelper(appsProxy.configValue().get("db").toString(),
				"reportType");
		form = type.getChecker();
	}

	private db bind() {
		return type.bind(String.valueOf(appsProxy.appid()));
	}

	// 新增
	public String AddType(String typeInfo) {
		form.putRule("TypeName", formdef.notNull);
		JSONObject object = JSONHelper.string2json(typeInfo);
		if (!form.checkRuleEx(object)) {
			return resultMessage(1);
		}
		if (findByName(object.get("TypeName").toString()) != null) {
			return resultMessage(2);
		}
		String info = bind().data(object).insertOnce().toString();
		return findById(info);
	}

	// 修改
	public String UpdateType(String id, String typeInfo) {
		JSONObject object = JSONHelper.string2json(typeInfo);
		if (object.containsKey("TypeName")) {
			if (findByName(object.get("TypeName").toString()) != null) {
				return resultMessage(2);
			}
		}

		int code = bind().eq("_id", new ObjectId(id)).data(object)
				.update() != null ? 0 : 99;
		return resultMessage(code, "修改成功");
	}

	// 删除
	public String DeleteType(String id) {
		int code = bind().eq("_id", new ObjectId(id)).delete() != null ? 0 : 99;
		return resultMessage(code, "删除成功");
	}

	// 批量删除
	public String DeleteBatchType(String ids) {
		String[] value = ids.split(",");
		int len = value.length;
		bind().or();
		for (int i = 0; i < len; i++) {
			bind().eq("_id", new ObjectId(value[i]));
		}
		int code = bind().deleteAll() == len ? 0 : 99;
		return resultMessage(code, "删除成功");
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String PageType(int ids, int pageSize) {
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
	public String search(int ids, int pageSize, String info) {
		bind().like("name",
				JSONHelper.string2json(info).get("TypeName").toString());
		JSONArray array = bind().dirty().page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) bind().count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", array);
		return resultMessage(object);
	}

	private JSONObject findByName(String name) {
		return bind().eq("TypeName", name).find();
	}

	public String findById(String id) {
		return resultMessage(bind().eq("_id", new ObjectId(id)).find());
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
