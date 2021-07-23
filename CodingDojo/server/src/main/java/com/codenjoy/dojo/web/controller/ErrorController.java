package com.codenjoy.dojo.web.controller;

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

import com.codenjoy.dojo.services.ErrorTicketService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Igor Petrov
 * Created at 5/23/2019
 */
@Controller
@RequestMapping(ErrorController.URI)
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    public static final String URI = "/error";
    public static final String JAVAX_SERVLET_ERROR_MESSAGE = "javax.servlet.error.message";
    public static final String JAVAX_SERVLET_ERROR_EXCEPTION = "javax.servlet.error.exception";

    @Autowired
    private ErrorTicketService ticket;

    @RequestMapping()
    public String error(HttpServletRequest req, ModelMap model) {
        Exception throwable = (Exception)req.getAttribute(JAVAX_SERVLET_ERROR_EXCEPTION);
        if (throwable != null) {
            return error(throwable, req, model);
        }

        String message = (String) req.getAttribute(JAVAX_SERVLET_ERROR_MESSAGE);
        if (!StringUtils.isEmpty(message)) {
            return error(message, req, model);
        }

        return error("Something wrong", req, model);
    }

    @GetMapping(params = "message")
    public String error(@RequestParam("message") String message, HttpServletRequest req, ModelMap model) {
        IllegalAccessException exception = new IllegalAccessException(message);
        return error(exception, req, model);
    }

    private String error(Exception exception, HttpServletRequest req, ModelMap model) {
        String url = req.getRequestURL().toString();

        ModelAndView view = ticket.get(url, exception);
        model.mergeAttributes(view.getModel());

        return view.getViewName();
    }

    @Override
    public String getErrorPath() {
        return URI;
    }
}
