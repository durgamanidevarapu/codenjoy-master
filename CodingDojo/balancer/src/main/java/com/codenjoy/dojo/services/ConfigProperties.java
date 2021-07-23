package com.codenjoy.dojo.services;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2018 - 2019 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.codenjoy.dojo.services.hash.Hash;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.List;

/**
 * Тут собраны только те проперти, которые важны в контроллерах.
 * Все дело в том, что я не хочу делать второго конфига который будет уметь
 * находить properties файлы вокруг приложения еще и в spring-context.xml
 * а это потому, что он не обрабатывается фильтрами maven при сборке в war.
 * Единственное место, где конфигурится *.properties - applicationContext.xml
 */
@Component
public class ConfigProperties {

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${email.hash}")
    private String emailHash;

    @Autowired
    private GameProperties gameProperties;

    public void updateFrom(ConfigProperties config) {
        BeanUtils.copyProperties(config, this);
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public String getGameType() {
        return gameProperties.getType();
    }

    public int getGameRoom() {
        return gameProperties.getRoom();
    }

    public String getGameFinalTime() {
        return gameProperties.getFinaleTime();
    }

    public List<String> getServers() {
        return gameProperties.getServers();
    }

    public String getEmail(String id) {
        return Hash.getEmail(id, emailHash);
    }

    public String getId(String email) {
        return Hash.getId(email, emailHash);
    }

    public String getAdminToken() {
        return DigestUtils.md5DigestAsHex(adminPassword.getBytes());
    }

    public String getDayStart() {
        return gameProperties.getStartDay();
    }

    public String getDayEnd() {
        return gameProperties.getEndDay();
    }

    public int getDayFinalistCount() {
        return gameProperties.getFinalistsCount();
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public void setEmailHash(String emailHash) {
        this.emailHash = emailHash;
    }

}
