import { describe, expect, it } from "vitest";
import { buildVehicleCommand, type VehicleEditorFields } from "./features/admin/vehicleEditor";

const form: VehicleEditorFields = {
  plateNumber: " 新H12345 ", category: "RESIDENT", vehicleType: "", status: "ACTIVE",
  ownerName: "测试村民", identityCardNumber: "测试证件", contactPhone: "",
  organizationName: "测试单位", passHolder: "", passageDetails: "",
  vehicleUse: "", passageArea: "", location: " 原位置 ", brandModel: "",
  approvedCapacity: "", plateColor: " 黄色 ", remarks: " 修改后的备注 ",
};

describe("车辆编辑写入协议", () => {
  it.each(["RESIDENT", "SCENIC_UNIT", "SCENIC_ENTERPRISE", "KANAS_TOURISM_DEVELOPMENT", "CADRE", "OTHER_LONG_TERM"])("类别 %s 的备注存入对应资料而非附加字段", category => {
    const command = buildVehicleCommand({ ...form, category });
    expect(command.attributes).toEqual({ position: "原位置", plateColor: "黄色" });
    expect(command.plateNumber).toBe("新H12345");
    expect(command.vehicleType).toBeNull();
    const profile = category === "RESIDENT" ? command.residentProfile : command.longTermProfile;
    expect(profile?.remarks).toBe("修改后的备注");
    expect(category === "RESIDENT" ? command.longTermProfile : command.residentProfile).toBeNull();
  });

  it("清空备注提交空值，空白附加字段省略", () => {
    const command = buildVehicleCommand({ ...form, remarks: "  ", location: "", plateColor: " " });
    expect(command.attributes).toEqual({});
    expect(command.residentProfile?.remarks).toBeNull();
    expect(form.remarks).toBe(" 修改后的备注 ");
  });

  it("六项补充资料均使用服务端支持的键", () => {
    const command = buildVehicleCommand({ ...form, vehicleUse: "用途", passageArea: "区域", brandModel: "品牌", approvedCapacity: "5" });
    expect(command.attributes).toEqual({ vehicleUse: "用途", passageArea: "区域", position: "原位置", brandModel: "品牌", approvedCapacity: "5", plateColor: "黄色" });
  });
});
