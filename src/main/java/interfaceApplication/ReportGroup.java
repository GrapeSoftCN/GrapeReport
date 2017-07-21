package interfaceApplication;

import org.json.simple.JSONObject;

import JGrapeSystem.jGrapeFW_Message;
import apps.appsProxy;
import database.DBHelper;
import database.db;
import json.JSONHelper;

public class ReportGroup {
	private DBHelper Rgroup;

	public ReportGroup() {
		Rgroup = new DBHelper(appsProxy.configValue().get("db").toString(),
				"ReportGroup");
	}
	private db bind() {
		return Rgroup.bind(appsProxy.appid() + "");
	}

	@SuppressWarnings("unchecked")
	public String AddRgroup(String info) {
		JSONObject object = JSONHelper.string2json(info);
		if (!object.containsKey("content")) {
			object.put("content", "");
		}
		Object object2 = bind().data(object).insertOnce();
		return resultMessage(0, object2 != null ? object2.toString() : "");
	}

	private String resultMessage(int num, String message) {
		String mString = "";
		switch (num) {
		case 0:
			mString = message;
			break;

		default:
			mString = "其他操作异常";
			break;
		}
		return jGrapeFW_Message.netMSG(num, mString);
	}
}
