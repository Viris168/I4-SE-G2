<%-- src/main/webapp/WEB-INF/jsp/products/list.jsp --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c"   uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html><head><meta charset="UTF-8"><title>Products</title><link rel="stylesheet" href="/css/site.css"></head>
<body>
<h1>Products (<c:out value="${currency}"/>)</h1>
<c:if test="${not empty message}"><p class="flash"><c:out value="${message}"/></p></c:if>
<form method="get"><input name="q" value="<c:out value='${q}'/>" placeholder="search"><button>Go</button></form>
<table>
  <tr><th>Name</th><th>Net</th><th>Gross</th><th>Stock</th></tr>
  <c:forEach var="p" items="${products}">
    <tr class="${p.stock < threshold ? 'low' : ''}">
      <td><c:out value="${p.name}"/></td>
      <td class="num"><fmt:formatNumber value="${p.price}" minFractionDigits="2"/></td>
      <td class="num"><fmt:formatNumber value="${p.gross}" minFractionDigits="2"/></td>
      <td class="num">${p.stock}</td>
    </tr>
  </c:forEach>
  <c:if test="${empty products}"><tr><td colspan="4">No products found.</td></tr></c:if>
</table>
<p class="hint">Rows highlighted have fewer than ${threshold} units in stock.</p>
<a href="/products/new">Add product</a>
</body></html>
