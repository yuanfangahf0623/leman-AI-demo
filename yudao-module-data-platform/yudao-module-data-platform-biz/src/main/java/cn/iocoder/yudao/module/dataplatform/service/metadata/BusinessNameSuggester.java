package cn.iocoder.yudao.module.dataplatform.service.metadata;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class BusinessNameSuggester {

    private static final Map<String, String> CORE_TABLE_NAMES = Map.ofEntries(
            Map.entry("comproduct", "产品"), Map.entry("comcustomer", "客户"),
            Map.entry("comdepartment", "部门"), Map.entry("comwarehouse", "仓库"),
            Map.entry("comwareamount", "仓库存量"), Map.entry("comperson", "人员"),
            Map.entry("ordbillmain", "订单主表"), Map.entry("ordbillsub", "订单明细"),
            Map.entry("stkbillmain", "库存单据主表"), Map.entry("stkbillsub", "库存单据明细"),
            Map.entry("accvouchermain", "会计凭证主表"), Map.entry("accvouchersub", "会计凭证明细")
    );

    private static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("id", "编号"), Map.entry("code", "编码"), Map.entry("no", "单号"),
            Map.entry("name", "名称"), Map.entry("title", "标题"), Map.entry("type", "类型"),
            Map.entry("kind", "类别"), Map.entry("category", "分类"), Map.entry("status", "状态"),
            Map.entry("state", "状态"), Map.entry("flag", "标志"), Map.entry("enabled", "启用"),
            Map.entry("date", "日期"), Map.entry("time", "时间"), Map.entry("year", "年度"),
            Map.entry("month", "月份"), Map.entry("day", "日"), Map.entry("begin", "开始"),
            Map.entry("start", "开始"), Map.entry("end", "结束"), Map.entry("create", "创建"),
            Map.entry("created", "创建"), Map.entry("update", "更新"), Map.entry("updated", "更新"),
            Map.entry("modify", "修改"), Map.entry("modified", "修改"), Map.entry("delete", "删除"),
            Map.entry("product", "产品"), Map.entry("item", "项目"), Map.entry("material", "物料"),
            Map.entry("customer", "客户"), Map.entry("supplier", "供应商"), Map.entry("vendor", "供应商"),
            Map.entry("department", "部门"), Map.entry("dept", "部门"), Map.entry("person", "人员"),
            Map.entry("employee", "员工"), Map.entry("user", "用户"), Map.entry("operator", "操作人"),
            Map.entry("creator", "创建人"), Map.entry("updater", "更新人"), Map.entry("owner", "负责人"),
            Map.entry("warehouse", "仓库"), Map.entry("ware", "仓库"), Map.entry("stock", "库存"),
            Map.entry("quantity", "数量"), Map.entry("qty", "数量"), Map.entry("amount", "金额"),
            Map.entry("price", "单价"), Map.entry("cost", "成本"), Map.entry("rate", "比率"),
            Map.entry("tax", "税"), Map.entry("currency", "币种"), Map.entry("unit", "单位"),
            Map.entry("order", "订单"), Map.entry("bill", "单据"), Map.entry("voucher", "凭证"),
            Map.entry("main", "主表"), Map.entry("master", "主表"), Map.entry("sub", "明细"),
            Map.entry("detail", "明细"), Map.entry("line", "行"), Map.entry("sequence", "序号"),
            Map.entry("seq", "序号"), Map.entry("remark", "备注"), Map.entry("memo", "备注"),
            Map.entry("note", "说明"), Map.entry("description", "描述"), Map.entry("desc", "描述"),
            Map.entry("address", "地址"), Map.entry("phone", "电话"), Map.entry("mobile", "手机号"),
            Map.entry("email", "邮箱"), Map.entry("contact", "联系人"), Map.entry("account", "账户"),
            Map.entry("bank", "银行"), Map.entry("password", "密码"), Map.entry("secret", "密钥"),
            Map.entry("token", "令牌"), Map.entry("source", "来源"), Map.entry("target", "目标"),
            Map.entry("parent", "上级"), Map.entry("level", "级别"), Map.entry("group", "分组"),
            Map.entry("company", "公司"), Map.entry("organization", "组织"), Map.entry("org", "组织")
    );

    public String suggestTableName(String tableName) {
        String exact = CORE_TABLE_NAMES.get(tableName.toLowerCase(Locale.ROOT));
        return exact != null ? exact : suggestWords(tableName);
    }

    public String suggestFieldName(String columnName, String sourceComment) {
        return StringUtils.hasText(sourceComment) ? sourceComment.trim() : suggestWords(columnName);
    }

    public String suggestBusinessDomain(String tableName) {
        String lower = tableName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("acc") || lower.contains("voucher")) return "财务";
        if (lower.startsWith("ord") || lower.contains("order")) return "订单";
        if (lower.startsWith("stk") || lower.contains("stock") || lower.contains("ware")) return "库存";
        if (lower.startsWith("pur") || lower.contains("purchase")) return "采购";
        if (lower.startsWith("sal") || lower.contains("sale")) return "销售";
        if (lower.startsWith("prd") || lower.startsWith("mps") || lower.startsWith("mrp")) return "生产计划";
        if (lower.startsWith("crm")) return "客户关系";
        if (lower.startsWith("ass")) return "资产";
        if (lower.startsWith("hr") || lower.contains("person") || lower.contains("employee")) return "人力资源";
        if (lower.startsWith("com")) return "基础资料";
        return "其他";
    }

    public String suggestSensitivity(String columnName) {
        String lower = columnName.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "password", "passwd", "secret", "token", "privatekey")) return "RESTRICTED";
        if (containsAny(lower, "idcard", "identity", "mobile", "phone", "email", "address", "bank", "account")) {
            return "SENSITIVE";
        }
        return "INTERNAL";
    }

    public String suggestClassification(String columnName) {
        String lower = columnName.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "password", "passwd", "secret", "token")) return "认证凭据";
        if (containsAny(lower, "idcard", "identity")) return "身份信息";
        if (containsAny(lower, "mobile", "phone", "email", "address", "contact")) return "联系信息";
        if (containsAny(lower, "bank", "account", "amount", "price", "cost")) return "财务信息";
        return null;
    }

    public boolean isIncrementalCandidate(String columnName, String dataType) {
        String name = columnName.toLowerCase(Locale.ROOT);
        String type = dataType.toLowerCase(Locale.ROOT);
        boolean temporalType = type.contains("date") || type.contains("time");
        return temporalType && containsAny(name, "update", "modify", "change", "create", "date", "time");
    }

    private String suggestWords(String value) {
        List<String> tokens = split(value);
        StringBuilder result = new StringBuilder();
        boolean translated = false;
        for (String token : tokens) {
            String word = WORDS.get(token.toLowerCase(Locale.ROOT));
            if (word != null) {
                result.append(word);
                translated = true;
            } else {
                result.append(token);
            }
        }
        return translated ? result.toString() : value;
    }

    private List<String> split(String value) {
        String normalized = value.replaceAll("[_\\-\\s]+", " ")
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
        List<String> result = new ArrayList<>();
        for (String token : normalized.split(" +")) {
            if (!token.isBlank()) result.add(token);
        }
        return result;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }
}
