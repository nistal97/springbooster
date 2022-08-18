package org.springboot.initializer.controller;

import org.springboot.initializer.SpringBooster;
import org.springboot.initializer.model.ConfigGraph;
import org.springboot.initializer.model.GenericType;
import org.springboot.initializer.util.BuiltInMethodFunc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ControllerMetaData extends SpringBooster.Base{
    private String validator;
    private List<Controller> controllers = new ArrayList<>();

    public List<Controller> getControllers() {
        return controllers;
    }

    public void setControllers(List<Controller> controllers) {
        this.controllers = controllers;
    }

    public String getValidator() {
        return validator;
    }

    public void setValidator(String validator) {
        this.validator = validator;
    }

}
