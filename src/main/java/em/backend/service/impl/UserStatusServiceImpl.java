package em.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import em.backend.mapper.UserStatusMapper;
import em.backend.pojo.UserStatus;
import em.backend.service.IUserStatusService;
import org.springframework.stereotype.Service;

@Service
public class UserStatusServiceImpl extends ServiceImpl<UserStatusMapper, UserStatus> implements IUserStatusService {
    // 可以实现自定义的业务方法
} 