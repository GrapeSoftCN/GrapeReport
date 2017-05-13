package interfaceApplication;

import org.bson.types.ObjectId;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import apps.appsProxy;
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

	// 新增类型
	public String AddType(String typeInfo) {
		form.putRule("TypeName", formdef.notNull);
		JSONObject object = JSONHelper.string2json(typeInfo);
		if (!form.checkRuleEx(object)) {
			return resultMessage(1);
		}
		if (findByName(object.get("TypeName").toString()) != null) {
			return resultMessage(2);
		}
		String info = type.data(object).insertOnce().toString();
		return resultMessage(findById(info));
	}

	// 修改
	public String UpdateType(String id, String typeInfo) {
		JSONObject object = JSONHelper.string2json(typeInfo);
		if (findByName(object.get("TypeName").toString()) != null) {
			return resultMessage(2);
		}
		int code = type.eq("_id", new ObjectId(id)).data(object)
				.update() != null ? 0 : 99;
		return resultMessage(code, "修改成功");
	}

	// 删除
	public String DeleteType(String id) {
		int code = type.eq("_id", new ObjectId(id)).delete() != null ? 0 : 99;
		return resultMessage(code, "删除成功");
	}

	// 批量删除删除
	public String DeleteBatchType(String ids) {
		String[] value = ids.split(",");
		int len = value.length;
		type.or();
		for (int i = 0; i < len; i++) {
			type.eq("_id", new ObjectId(value[i]));
		}
		int code = type.deleteAll() == len ? 0 : 99;
		return resultMessage(code, "删除成功");
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String PageType(int ids, int pageSize) {
		JSONArray array = type.page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) type.count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", array);
		return resultMessage(object);
	}

	// 分页模糊查询
	@SuppressWarnings("unchecked")
	public String search(int ids, int pageSize, String info) {
		type.like("name",
				JSONHelper.string2json(info).get("TypeName").toString());
		JSONArray array = type.dirty().page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) type.count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", array);
		return resultMessage(object);
	}

	public JSONObject findByName(String name) {
		return type.eq("TypeName", name).find();
	}

	public JSONObject findById(String id) {
		return type.eq("_id", new ObjectId(id)).find();
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
			msg = "该类型已存在";
			break;
		default:
			msg = "其他操作错误";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
