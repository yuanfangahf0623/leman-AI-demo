package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.dal.mysql.SharedHrMapper;
import cn.iocoder.yudao.server.framework.security.SecurityFrameworkUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SharedHrService {
    private final SharedHrMapper mapper;
    private Long tenant() {
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getTenantId() == null) throw new ServiceException(401, "请先登录");
        return user.getTenantId();
    }
    public List<Map<String,Object>> organizations() { return mapper.organizations(tenant()); }
    public List<Map<String,Object>> people(int page, int size) { return mapper.people(tenant(),size,(page-1)*size); }
    public Map<String,Object> status() { return mapper.status(tenant()); }
}
