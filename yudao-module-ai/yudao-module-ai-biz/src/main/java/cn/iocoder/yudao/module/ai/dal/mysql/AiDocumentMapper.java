package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 文档 Mapper。
 */
@Mapper
public interface AiDocumentMapper extends BaseMapper<AiDocumentDO> {
}
