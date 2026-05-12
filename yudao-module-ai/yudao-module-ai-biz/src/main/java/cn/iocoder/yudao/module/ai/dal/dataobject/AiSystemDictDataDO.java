package cn.iocoder.yudao.module.ai.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统字典数据 DO。
 */
@TableName("system_dict_data")
@Data
public class AiSystemDictDataDO {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("sort")
    private Integer sort;
    @TableField("label")
    private String label;
    @TableField("value")
    private String value;
    @TableField("dict_type")
    private String dictType;
    @TableField("status")
    private Integer status;
    @TableField("remark")
    private String remark;
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

}
