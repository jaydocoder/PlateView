export const adminModules = [
  { title: "车辆档案", tone: "green", primary: false },
  { title: "账号管理", tone: "teal", primary: false },
  { title: "导入中心", tone: "amber", primary: false },
  { title: "审计日志", tone: "slate", primary: false },
  { title: "排班规划", tone: "teal", primary: true },
  { title: "微信同步", tone: "green", primary: true },
  { title: "数据访问控制", tone: "amber", primary: true },
] as const;

export const visibleAdminModules = (primary: boolean) => adminModules.filter(module => !module.primary || primary);
