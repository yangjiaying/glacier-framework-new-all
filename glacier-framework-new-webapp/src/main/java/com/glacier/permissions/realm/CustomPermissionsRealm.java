package com.glacier.permissions.realm;

import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ByteSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.glacier.permission.dao.AuthorityMapper;
import com.glacier.permission.dao.RoleMapper;
import com.glacier.permission.dao.UserMapper;
import com.glacier.permission.entity.Authority;
import com.glacier.permission.entity.User;
import com.glacier.permission.entity.UserExample;
import com.glacier.permission.entity.util.ActionRange;
import com.glacier.permissions.service.UserService;
import com.glacier.security.util.Encodes;

/**
 * 
 * @ClassName: CustomPermissionsRealm
 * @Description: TODO(自定义执行认证和授权的类)
 * @author zhenfei.zhang
 * @email zhangzhenfei_email@163.com
 * @date 2012-12-27 下午2:03:58
 */
public class CustomPermissionsRealm extends AuthorizingRealm {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private AuthorityMapper authorityMapper;

    public CustomPermissionsRealm() {
        setName("CustomPermissionsRealm");
    }

    /*
     * (non-Javadoc) <p>Title: doGetAuthorizationInfo</p> <p>Description: 授权</p>
     * <p>当缓存中没有用户的授权信息的时候会从该方法中加载，缓存到文件中</p>
     * 
     * @param principals
     * 
     * @return
     * 
     * @see
     * org.apache.shiro.realm.AuthorizingRealm#doGetAuthorizationInfo(org.apache
     * .shiro.subject.PrincipalCollection)
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        Subject pricipalSubject = SecurityUtils.getSubject();
        User principalUser = (User)pricipalSubject.getPrincipal();// 获取通过认证用户
        List<Authority> authorityList = authorityMapper.selectByUserId(principalUser.getUserId());// 获取用户的权限集合(用户和用户组的角色权限)
        if (null != authorityList && authorityList.size() > 0) {
            SimpleAuthorizationInfo authInfo = new SimpleAuthorizationInfo();// 创建返回权限对象
            for (Authority authority : authorityList) {
                if (null != authority && StringUtils.isNotBlank(authority.getActions())) {
                    String[] actionString = authority.getActions().split(",");
                    for (String action : actionString) {
                        // shiro权限字符串为：“当前资源英文名称:操作名英文名称”
                        // 对action进行分割，避免权限范围对其造成的影响
                        String [] authStr = action.split(":");
                        authInfo.addStringPermission(authority.getMenuEnName() + ":" + authStr[0]);// 设置权限操作(设置Permission)
                        
                        //建立操作权限范围
                        ActionRange actionRange = (ActionRange)pricipalSubject.getSession().getAttribute(authStr[0]);
                        if(null != actionRange){//如果存在同一个操作数据权限，取出比对更新
                            if(actionRange.getValue() < ActionRange.valueOf(authStr[1]).getValue()){
                                pricipalSubject.getSession().setAttribute(authStr[0], ActionRange.valueOf(authStr[1]));//更新session
                            }
                        }else{//如果不存在操作，放入
                            pricipalSubject.getSession().setAttribute(authStr[0], ActionRange.valueOf(authStr[1]));//更新session
                        }
                    }
                }
            }
            return authInfo;
        }
        return null;
    }

    /*
     * (non-Javadoc) <p>Title: doGetAuthenticationInfo</p> <p>Description:
     * 认证回调函数,登录时调用.</p>
     * 
     * @param authtoken
     * 
     * @return
     * 
     * @throws AuthenticationException
     * 
     * @see
     * org.apache.shiro.realm.AuthenticatingRealm#doGetAuthenticationInfo(org
     * .apache.shiro.authc.AuthenticationToken)
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authcToken) throws AuthenticationException {
        CaptchaUsernamePasswordToken token = (CaptchaUsernamePasswordToken) authcToken;
        String username = token.getUsername();
        if (null != username && !"".equals(username)) {
            UserExample userExample = new UserExample();
            userExample.createCriteria().andUsernameEqualTo(username);
            User principalUser = userMapper.selectByExample(userExample).get(0);
            if (null != principalUser) {
                // 用户状态为启用或隐藏让其通过认证
                byte[] salt = Encodes.decodeHex(principalUser.getSalt());
                AuthenticationInfo info = new SimpleAuthenticationInfo(principalUser, principalUser.getPassword(), ByteSource.Util.bytes(salt), getName());// 将用户的所有信息作为认证对象返回
                clearCache(info.getPrincipals());// 认证成功后清除之前的缓存
                return info;
            } else {
                throw new DisabledAccountException();
            }
        }
        return null;
    }

    /**
     * 更新用户授权信息缓存.
     */
    public void clearCachedAuthorizationInfo(String principal) {
        SimplePrincipalCollection principals = new SimplePrincipalCollection(principal, getName());
        clearCachedAuthorizationInfo(principals);
    }

    /**
     * 清除所有用户授权信息缓存.
     */
    public void clearAllCachedAuthorizationInfo() {
        Cache<Object, AuthorizationInfo> cache = getAuthorizationCache();
        if (cache != null) {
            for (Object key : cache.keys()) {
                cache.remove(key);
            }
        }
    }

    /**
     * 设定Password校验的Hash算法与迭代次数.
     */
    @PostConstruct
    public void initCredentialsMatcher() {
        HashedCredentialsMatcher matcher = new HashedCredentialsMatcher(UserService.HASH_ALGORITHM);
        matcher.setHashIterations(UserService.HASH_INTERATIONS);
        setCredentialsMatcher(matcher);
    }

}
