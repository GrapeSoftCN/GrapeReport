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

public class Report {
	private static DBHelper report;
	private static formHelper form;
	private JSONObject _obj = new JSONObject();

	static {
		report = new DBHelper(appsProxy.configValue().get("db").toString(),
				"reportInfo");
		form = report.getChecker();
	}

	// 新增
	public String AddReport(String ReportInfo) {
		JSONObject object = JSONHelper.string2json(ReportInfo);
		int mode = Integer.parseInt(object.get("mode").toString());
		if (mode == 0) {
			setCheck();
			form.putRule("Informant", formdef.notNull);
			form.putRule("InformantPhone", formdef.notNull);
		}
		if (mode == 1) {
			setCheck();
		}
		if (!form.checkRuleEx(object)) {
			return resultMessage(1);
		}

		String info = report.data(object).insertOnce().toString();
		return resultMessage(findById(info));
	}

	// 修改
	public String UpdateReporte(String id, String typeInfo) {
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

	// 批量删除删除
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
		object.put("data", array);
		return resultMessage(object);
	}

	// 分页模糊查询
	@SuppressWarnings("unchecked")
	public String search(int ids, int pageSize, String info) {
		JSONObject objects = JSONHelper.string2json(info);
		for (Object obj : objects.keySet()) {
			if (obj.equals("_id")) {
				report.like("_id", new ObjectId(objects.get("_id").toString()));
			}
			report.like(obj.toString(), objects.get(obj.toString()).toString());
		}
		JSONArray array = report.dirty().page(ids, pageSize);
		JSONObject object = new JSONObject();
		object.put("totalSize",
				(int) Math.ceil((double) report.count() / pageSize));
		object.put("pageSize", pageSize);
		object.put("currentPage", ids);
		object.put("data", array);
		return resultMessage(object);
	}

	// 显示个人相关的举报件
	public String searchById(String userid) {
		JSONArray array = report.eq("userid", userid).limit(20).select();
		return resultMessage(array);
	}

	// 显示含有反馈信息，但是用户未读的举报件
	public String ShowFeed() {
		JSONArray array = report.eq("feedback", 1).eq("read", 0).limit(20)
				.select();
		return resultMessage(array);
	}

	//到处事件信息到word，并支持打印功能
	public String printWord(){
		return null;
	}
	public JSONObject findById(String id) {
		return report.eq("_id", new ObjectId(id)).find();
	}

	// 设置验证字段
	private formHelper setCheck() {
		form.putRule("Wrongdoer", formdef.notNull);
		form.putRule("WrongdoerSex", formdef.notNull);
		form.putRule("content", formdef.notNull);
		return form;
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
			msg = "必填字段为空";
			break;
		default:
			msg = "其他操作错误";
			break;
		}
		return jGrapeFW_Message.netMSG(num, msg);
	}
}
