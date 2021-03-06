#!/bin/sh

sbt clean \
  ++2.10.4 \
  common/publishLocal \
  assets/publishLocal \
  orm/publishLocal \
  factoryGirl/publishLocal \
  validator/publishLocal \
  framework/publishLocal \
  mailer/publishLocal \
  standalone/publishLocal \
  task/publishLocal \
  test/publishLocal \
  freemarker/publishLocal \
  thymeleaf/publishLocal 

