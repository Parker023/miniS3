package com.anirudh.storage.minis3.user.mapper;

import com.anirudh.storage.minis3.user.dto.UserRegistrationRequest;
import com.anirudh.storage.minis3.user.dto.UserRegistrationResponse;
import com.anirudh.storage.minis3.user.entity.User;
import com.anirudh.storage.minis3.util.CommonUtils;
import com.anirudh.storage.minis3.util.PasswordUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.stereotype.Component;

/**
 * @author Anirudh
 * @since 21/02/26
 */

@Component
@RequiredArgsConstructor
public class UserMapper {
    private final ModelMapper modelMapper;
    private final PasswordUtil passwordUtil;

    @PostConstruct
    public void configureMappings() {

        Converter<String, String> passwordConverter =
                ctx -> passwordUtil.encode(ctx.getSource());

        TypeMap<UserRegistrationRequest, User> typeMap =
                modelMapper.createTypeMap(UserRegistrationRequest.class, User.class);

        typeMap.addMappings(mapper -> {

            mapper.using(passwordConverter)
                    .map(UserRegistrationRequest::getPassword, User::setPassword);
        });
    }

    public UserRegistrationResponse entityToDTO(User user) {
        return modelMapper.map(user, UserRegistrationResponse.class);


    }

    public User dtoToEntity(UserRegistrationRequest request) {
        return modelMapper.map(request, User.class);
    }

}
