package cn.iocoder.yudao.module.ai.dal.mysql;

import cn.iocoder.yudao.module.ai.dal.dataobject.AiDocumentChunkDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 文档切片 Mapper。
 */
@Mapper
public interface AiDocumentChunkMapper extends BaseMapper<AiDocumentChunkDO> {
}
