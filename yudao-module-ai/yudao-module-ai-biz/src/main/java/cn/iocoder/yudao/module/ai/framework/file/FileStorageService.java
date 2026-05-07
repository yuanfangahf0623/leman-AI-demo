package cn.iocoder.yudao.module.ai.framework.file;

/**
 * 文件存储服务抽象。
 *
 * <p>当前开发阶段先使用本地文件系统实现。后续接入 MinIO 时，只需要新增实现类，
 * 文档上传 Service 不需要感知底层存储变化。</p>
 */
public interface FileStorageService {

    /**
     * 按服务端生成的 objectKey 保存文件内容。
     *
     * @param objectKey 服务端生成的对象 Key，不能直接使用用户上传文件名
     * @param content 文件内容
     * @return 文件存储结果
     */
    FileStorageResult store(String objectKey, byte[] content);

}
