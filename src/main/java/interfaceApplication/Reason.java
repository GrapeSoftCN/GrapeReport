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
		Reason = new DBHelper(appsProxy.configValue().get("db").toString(),
				"reportReson");
		form = Reason.getChecker();
	}

	// 新增
	@SuppressWarnings("unchecked")
	public String AddReson(String info) {
		form.putRule("Rcontent", formdef.notNull);
		JSONObject object = JSONHelper.string2json(info);
		object.put("count", 0);
		if (!form.checkRuleEx(object)) {
			return resultMessage(1);
		}
		if (findByName(object.get("Rcontent").toString()) != null) {
			return resultMessage(2);
		}
		String code = Reason.data(object).insertOnce().toString();
		return resultMessage(findById(code));
	}

	// 修改
	public String UpdateReson(String id, String Info) {
		JSONObject object = JSONHelper.string2json(Info);
		if (object.containsKey("Rcontent")) {
			if (findByName(object.get("Rcontent").toString()) != null) {
				return resultMessage(2);
			}
		}
		int code = Reason.eq("_id", new ObjectId(id)).data(object)
				.update() != null ? 0 : 99;
		return resultMessage(code, "修改成功");
	}

	// 删除
	public String DeleteReson(String id) {
		int code = Reason.eq("_id", new ObjectId(id)).delete() != null ? 0 : 99;
		return resultMessage(code, "删除成功");
	}

	// 批量删除
	public String DeleteBatchReson(String ids) {
		String[] value = ids.split(",");
		int len = value.length;
		Reason.or();
		for (int i = 0; i < len; i++) {
			Reason.eq("_id", new ObjectId(value[i]));
		}
		int code = Reason.deleteAll() == len ? 0 : 99;
		return resultMessage(code, "删除成功");
	}

	// 分页
	@SuppressWarnings("unchecked")
	public String PageReson(int ids, int pageSize) {
		JSONArray array = Reason.desc("count").page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) Reason.count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", array);
		return resultMessage(object);
	}

	// 搜索
	@SuppressWarnings("unchecked")
	public String search(int ids, int pageSize, String info) {
		Reason.like("Rcontent",
				JSONHelper.string2json(info).get("Rcontent").toString());
		JSONArray array = Reason.dirty().desc("count").page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) Reason.count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", array);
		return resultMessage(object);
	}

	// 事由使用次数+1
	@SuppressWarnings("unchecked")
	public String addTime(String name) {
		Reason.eq("Rcontent", name);
		JSONObject object = Reason.dirty().find();
		object.put("count",
				Integer.parseInt(object.get("count").toString()) + 1);
		return Reason.data(object).update() != null ? resultMessage(0)
				: resultMessage(99);
	}

	public JSONObject findByName(String name) {
		return Reason.eq("Rcontent", name).find();
	}

	public JSONObject findById(String id) {
		return Reason.eq("_id", new ObjectId(id)).find();
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
