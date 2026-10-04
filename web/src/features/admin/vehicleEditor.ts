export type VehicleEditorFields = {
  plateNumber: string;
  category: string;
  vehicleType: string;
  status: string;
  ownerName: string;
  identityCardNumber: string;
  contactPhone: string;
  organizationName: string;
  passHolder: string;
  passageDetails: string;
  vehicleUse: string;
  passageArea: string;
  location: string;
  brandModel: string;
  approvedCapacity: string;
  plateColor: string;
  remarks: string;
};

export function buildVehicleCommand(form: VehicleEditorFields) {
  const optionalText = (value: string) => value.trim() || null;
  // 附加字段与资料字段分别遵循服务端写入协议，备注不属于附加字段。
  const attributes = Object.fromEntries([
    ["vehicleUse", form.vehicleUse],
    ["passageArea", form.passageArea],
    ["position", form.location],
    ["brandModel", form.brandModel],
    ["approvedCapacity", form.approvedCapacity],
    ["plateColor", form.plateColor],
  ].map(([key, value]) => [key, value.trim()]).filter(([, value]) => value !== ""));
  return {
    plateNumber: form.plateNumber.trim(),
    category: form.category,
    vehicleType: optionalText(form.vehicleType),
    status: form.status,
    attributes,
    residentProfile: form.category === "RESIDENT" ? {
      ownerName: form.ownerName,
      identityCardNumber: form.identityCardNumber,
      contactPhone: optionalText(form.contactPhone),
      remarks: optionalText(form.remarks),
    } : null,
    longTermProfile: form.category !== "RESIDENT" ? {
      organizationName: optionalText(form.organizationName),
      passHolder: optionalText(form.passHolder),
      passageDetails: optionalText(form.passageDetails),
      remarks: optionalText(form.remarks),
    } : null,
  };
}
