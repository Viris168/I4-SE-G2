<%-- src/main/webapp/WEB-INF/jsp/products/form.jsp --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c"    uri="jakarta.tags.core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>New product</title><link rel="stylesheet" href="/css/site.css"></head>
<body>
<h1>New product</h1>
<form:form method="post" action="/products" modelAttribute="product">
  <label>Name
    <form:input path="name" maxlength="80" cssErrorClass="invalid"/>
    <form:errors path="name" cssClass="error" element="span"/>
  </label>
  <label>Price (<c:out value="${currency}"/>, net)
    <form:input path="price" cssErrorClass="invalid"/>
    <form:errors path="price" cssClass="error" element="span"/>
  </label>
  <label>Stock
    <form:input path="stock" cssErrorClass="invalid"/>
    <form:errors path="stock" cssClass="error" element="span"/>
  </label>
  <button>Save</button>
  <a href="/products">Cancel</a>
</form:form>
</body></html>
