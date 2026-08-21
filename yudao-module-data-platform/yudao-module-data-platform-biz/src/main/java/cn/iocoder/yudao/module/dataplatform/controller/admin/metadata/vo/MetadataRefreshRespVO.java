package cn.iocoder.yudao.module.dataplatform.controller.admin.metadata.vo;

public record MetadataRefreshRespVO(int tableCount, int fieldCount, int sourceCommentCount,
                                    int generatedNameCount) {
}
