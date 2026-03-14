package org.javarosa.xpath.test

import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.LivePrototypeFactory
import org.javarosa.core.util.test.ExternalizableTest
import org.javarosa.xpath.XPathParseTool
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.parser.XPathSyntaxException
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class XPathParseTest(
    private val inputString: String,
    private val expectedParseOutput: String?
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: test({0}) expected={1}")
        fun testData(): Iterable<Array<String?>> {
            return listOf(
                    // no null expressions
                    arrayOf("", null),
                    arrayOf("     ", null),
                    arrayOf("  \t \n  \r ", null),
                    // numbers
                    arrayOf("10", "{num:10.0}"),
                    arrayOf("123.", "{num:123.0}"),
                    arrayOf("734.04", "{num:734.04}"),
                    arrayOf("0.12345", "{num:0.12345}"),
                    arrayOf(".666", "{num:0.666}"),
                    arrayOf("00000333.3330000", "{num:333.333}"),
                    arrayOf("1230000000000000000000", "{num:1.23E21}"),
                    arrayOf("0.00000000000000000123", "{num:1.23E-18}"),
                    arrayOf("0", "{num:0.0}"),
                    arrayOf("0.", "{num:0.0}"),
                    arrayOf(".0", "{num:0.0}"),
                    arrayOf("0.0", "{num:0.0}"),
                    // strings
                    arrayOf("\"\"", "{str:''}"),
                    arrayOf("\"   \"", "{str:'   '}"),
                    arrayOf("''", "{str:''}"),
                    arrayOf("'\"'", "{str:'\"'}"),
                    arrayOf("\"'\"", "{str:'''}"),
                    arrayOf("'mary had a little lamb'", "{str:'mary had a little lamb'}"),
                    arrayOf("'unterminated string...", null),
                    // variables
                    arrayOf("\$var", "{var:var}"),
                    arrayOf("\$qualified:name", "{var:qualified:name}"),
                    arrayOf("\$x:*", null),
                    arrayOf("\$", null),
                    arrayOf("\$\$asdf", null),
                    // parens nesting
                    arrayOf("(5)", "{num:5.0}"),
                    arrayOf("(( (( (5 )) )))  ", "{num:5.0}"),
                    arrayOf(")", null),
                    arrayOf("(", null),
                    arrayOf("()", null),
                    arrayOf("(((3))", null),
                    // operators
                    arrayOf("5 + 5", "{binop-expr:+,{num:5.0},{num:5.0}}"),
                    arrayOf("-5", "{unop-expr:num-neg,{num:5.0}}"),
                    arrayOf("- 5", "{unop-expr:num-neg,{num:5.0}}"),
                    arrayOf("----5", "{unop-expr:num-neg,{unop-expr:num-neg,{unop-expr:num-neg,{unop-expr:num-neg,{num:5.0}}}}}"),
                    arrayOf("6 * - 7", "{binop-expr:*,{num:6.0},{unop-expr:num-neg,{num:7.0}}}"),
                    arrayOf("0--0", "{binop-expr:-,{num:0.0},{unop-expr:num-neg,{num:0.0}}}"),
                    arrayOf("+-", null),
                    arrayOf("5 * 5", "{binop-expr:*,{num:5.0},{num:5.0}}"),
                    arrayOf("5 div 5", "{binop-expr:/,{num:5.0},{num:5.0}}"),
                    arrayOf("5/5", null),
                    arrayOf("5 mod 5", "{binop-expr:%,{num:5.0},{num:5.0}}"),
                    arrayOf("5%5", null),
                    arrayOf("3mod4", "{binop-expr:%,{num:3.0},{num:4.0}}"),
                    arrayOf("5 divseparate-token", "{binop-expr:/,{num:5.0},{path-expr:rel,{{step:child,separate-token}}}}"),
                    arrayOf("5 = 5", "{binop-expr:==,{num:5.0},{num:5.0}}"),
                    arrayOf("5 != 5", "{binop-expr:!=,{num:5.0},{num:5.0}}"),
                    arrayOf("5 == 5", null),
                    arrayOf("5 <> 5", null),
                    arrayOf("5 < 5", "{binop-expr:<,{num:5.0},{num:5.0}}"),
                    arrayOf("5 <= 5", "{binop-expr:<=,{num:5.0},{num:5.0}}"),
                    arrayOf("5 > 5", "{binop-expr:>,{num:5.0},{num:5.0}}"),
                    arrayOf("5 >= 5", "{binop-expr:>=,{num:5.0},{num:5.0}}"),
                    arrayOf(">=", null),
                    arrayOf("'asdf'!=", null),
                    arrayOf("5 and 5", "{binop-expr:and,{num:5.0},{num:5.0}}"),
                    arrayOf("5 or 5", "{binop-expr:or,{num:5.0},{num:5.0}}"),
                    arrayOf("5 | 5", "{binop-expr:union,{num:5.0},{num:5.0}}"),
                    // operator associativity
                    arrayOf("1 or 2 or 3", "{binop-expr:or,{num:1.0},{binop-expr:or,{num:2.0},{num:3.0}}}"),
                    arrayOf("1 and 2 and 3", "{binop-expr:and,{num:1.0},{binop-expr:and,{num:2.0},{num:3.0}}}"),
                    arrayOf("1 = 2 != 3 != 4 = 5", "{binop-expr:==,{binop-expr:!=,{binop-expr:!=,{binop-expr:==,{num:1.0},{num:2.0}},{num:3.0}},{num:4.0}},{num:5.0}}"),
                    arrayOf("1 < 2 >= 3 <= 4 > 5", "{binop-expr:>,{binop-expr:<=,{binop-expr:>=,{binop-expr:<,{num:1.0},{num:2.0}},{num:3.0}},{num:4.0}},{num:5.0}}"),
                    arrayOf("1 + 2 - 3 - 4 + 5", "{binop-expr:+,{binop-expr:-,{binop-expr:-,{binop-expr:+,{num:1.0},{num:2.0}},{num:3.0}},{num:4.0}},{num:5.0}}"),
                    arrayOf("1 mod 2 div 3 div 4 * 5", "{binop-expr:*,{binop-expr:/,{binop-expr:/,{binop-expr:%,{num:1.0},{num:2.0}},{num:3.0}},{num:4.0}},{num:5.0}}"),
                    arrayOf("1|2|3", "{binop-expr:union,{binop-expr:union,{num:1.0},{num:2.0}},{num:3.0}}"),
                    // operator precedence
                    arrayOf("1 < 2 = 3 > 4 and 5 <= 6 != 7 >= 8 or 9 and 10",
                            "{binop-expr:or,{binop-expr:and,{binop-expr:==,{binop-expr:<,{num:1.0},{num:2.0}},{binop-expr:>,{num:3.0},{num:4.0}}},{binop-expr:!=,{binop-expr:<=,{num:5.0},{num:6.0}},{binop-expr:>=,{num:7.0},{num:8.0}}}},{binop-expr:and,{num:9.0},{num:10.0}}}"),
                    arrayOf("1 * 2 + 3 div 4 < 5 mod 6 | 7 - 8",
                            "{binop-expr:<,{binop-expr:+,{binop-expr:*,{num:1.0},{num:2.0}},{binop-expr:/,{num:3.0},{num:4.0}}},{binop-expr:-,{binop-expr:%,{num:5.0},{binop-expr:union,{num:6.0},{num:7.0}}},{num:8.0}}}"),
                    arrayOf("- 4 * 6", "{binop-expr:*,{unop-expr:num-neg,{num:4.0}},{num:6.0}}"),
                    arrayOf("8|-9", null),
                    arrayOf("6*(3+4)and(5or2)", "{binop-expr:and,{binop-expr:*,{num:6.0},{binop-expr:+,{num:3.0},{num:4.0}}},{binop-expr:or,{num:5.0},{num:2.0}}}"),
                    // function calls
                    arrayOf("function()", "{func-expr:function,{}}"),
                    // test built-in xpath function parsing / serialization
                    arrayOf("abs(1)", "{func-expr:abs,{{num:1.0}}}"),
                    arrayOf("acos(1)", "{func-expr:acos,{{num:1.0}}}"),
                    arrayOf("asin(1)", "{func-expr:asin,{{num:1.0}}}"),
                    arrayOf("atan(1)", "{func-expr:atan,{{num:1.0}}}"),
                    arrayOf("atan2(1,1)", "{func-expr:atan2,{{num:1.0},{num:1.0}}}"),
                    arrayOf("boolean(1)", "{func-expr:boolean,{{num:1.0}}}"),
                    arrayOf("boolean-from-string(1)", "{func-expr:boolean-from-string,{{num:1.0}}}"),
                    arrayOf("ceiling(1)", "{func-expr:ceiling,{{num:1.0}}}"),
                    arrayOf("checklist(1, 1)", "{func-expr:checklist,{{num:1.0},{num:1.0}}}"),
                    arrayOf("coalesce(1)", "{func-expr:coalesce,{{num:1.0}}}"),
                    arrayOf("concat(1)", "{func-expr:concat,{{num:1.0}}}"),
                    arrayOf("cond(1,2,3)", "{func-expr:cond,{{num:1.0},{num:2.0},{num:3.0}}}"),
                    arrayOf("contains(1,1)", "{func-expr:contains,{{num:1.0},{num:1.0}}}"),
                    arrayOf("cos(1)", "{func-expr:cos,{{num:1.0}}}"),
                    arrayOf("count(1)", "{func-expr:count,{{num:1.0}}}"),
                    arrayOf("count-selected(1)", "{func-expr:count-selected,{{num:1.0}}}"),
                    arrayOf("date(1)", "{func-expr:date,{{num:1.0}}}"),
                    arrayOf("depend(1)", "{func-expr:depend,{{num:1.0}}}"),
                    arrayOf("distance(1,1)", "{func-expr:distance,{{num:1.0},{num:1.0}}}"),
                    arrayOf("double(1)", "{func-expr:double,{{num:1.0}}}"),
                    arrayOf("ends-with(1,1)", "{func-expr:ends-with,{{num:1.0},{num:1.0}}}"),
                    arrayOf("exp(1)", "{func-expr:exp,{{num:1.0}}}"),
                    arrayOf("false()", "{func-expr:false,{}}"),
                    arrayOf("floor(1)", "{func-expr:floor,{{num:1.0}}}"),
                    arrayOf("format-date-for-calendar(1,1)", "{func-expr:format-date-for-calendar,{{num:1.0},{num:1.0}}}"),
                    arrayOf("format-date(1,1)", "{func-expr:format-date,{{num:1.0},{num:1.0}}}"),
                    arrayOf("if(1,1,1)", "{func-expr:if,{{num:1.0},{num:1.0},{num:1.0}}}"),
                    arrayOf("int(1)", "{func-expr:int,{{num:1.0}}}"),
                    arrayOf("join(1)", "{func-expr:join,{{num:1.0}}}"),
                    arrayOf("log(1)", "{func-expr:log,{{num:1.0}}}"),
                    arrayOf("log10(1)", "{func-expr:log10,{{num:1.0}}}"),
                    arrayOf("lower-case(1)", "{func-expr:lower-case,{{num:1.0}}}"),
                    arrayOf("max(1)", "{func-expr:max,{{num:1.0}}}"),
                    arrayOf("min(1)", "{func-expr:min,{{num:1.0}}}"),
                    arrayOf("not(1)", "{func-expr:not,{{num:1.0}}}"),
                    arrayOf("now()", "{func-expr:now,{}}"),
                    arrayOf("number(1)", "{func-expr:number,{{num:1.0}}}"),
                    arrayOf("pi()", "{func-expr:pi,{}}"),
                    arrayOf("position()", "{func-expr:position,{}}"),
                    arrayOf("pow(1,1)", "{func-expr:pow,{{num:1.0},{num:1.0}}}"),
                    arrayOf("regex(1,1)", "{func-expr:regex,{{num:1.0},{num:1.0}}}"),
                    arrayOf("replace(1,1,1)", "{func-expr:replace,{{num:1.0},{num:1.0},{num:1.0}}}"),
                    arrayOf("round(1)", "{func-expr:round,{{num:1.0}}}"),
                    arrayOf("selected-at(1,1)", "{func-expr:selected-at,{{num:1.0},{num:1.0}}}"),
                    arrayOf("selected(1,1)", "{func-expr:selected,{{num:1.0},{num:1.0}}}"),
                    arrayOf("sin(1)", "{func-expr:sin,{{num:1.0}}}"),
                    arrayOf("sqrt(1)", "{func-expr:sqrt,{{num:1.0}}}"),
                    arrayOf("starts-with(1,1)", "{func-expr:starts-with,{{num:1.0},{num:1.0}}}"),
                    arrayOf("string(1)", "{func-expr:string,{{num:1.0}}}"),
                    arrayOf("string-length(1)", "{func-expr:string-length,{{num:1.0}}}"),
                    arrayOf("substr(1,1,1)", "{func-expr:substr,{{num:1.0},{num:1.0},{num:1.0}}}"),
                    arrayOf("substring-after(1,1)", "{func-expr:substring-after,{{num:1.0},{num:1.0}}}"),
                    arrayOf("substring-before(1,1)", "{func-expr:substring-before,{{num:1.0},{num:1.0}}}"),
                    arrayOf("sum(1)", "{func-expr:sum,{{num:1.0}}}"),
                    arrayOf("tan(1)", "{func-expr:tan,{{num:1.0}}}"),
                    arrayOf("today()", "{func-expr:today,{}}"),
                    arrayOf("translate(1,1,1)", "{func-expr:translate,{{num:1.0},{num:1.0},{num:1.0}}}"),
                    arrayOf("true()", "{func-expr:true,{}}"),
                    arrayOf("upper-case(1)", "{func-expr:upper-case,{{num:1.0}}}"),
                    arrayOf("weighted-checklist(1, 1)", "{func-expr:weighted-checklist,{{num:1.0},{num:1.0}}}"),
                    arrayOf("func:tion()", "{func-expr:func:tion,{}}"),
                    arrayOf("function(   )", "{func-expr:function,{}}"),
                    arrayOf("function (5)", "{func-expr:function,{{num:5.0}}}"),
                    arrayOf("function   ( 5, 'arg', 4 * 12)", "{func-expr:function,{{num:5.0},{str:'arg'},{binop-expr:*,{num:4.0},{num:12.0}}}}"),
                    arrayOf("function ( 4, 5, 6 ", null),
                    arrayOf("4andfunc()", "{binop-expr:and,{num:4.0},{func-expr:func,{}}}"),
                    // function calls that are actually node tests
                    arrayOf("node()", "{path-expr:rel,{{step:child,node()}}}"),
                    arrayOf("text()", "{path-expr:rel,{{step:child,text()}}}"),
                    arrayOf("comment()", "{path-expr:rel,{{step:child,comment()}}}"),
                    arrayOf("processing-instruction()", "{path-expr:rel,{{step:child,proc-instr()}}}"),
                    arrayOf("processing-instruction('asdf')", "{path-expr:rel,{{step:child,proc-instr('asdf')}}}"),
                    arrayOf("node(5)", null),
                    arrayOf("text('str')", null),
                    arrayOf("comment(name)", null),
                    arrayOf("processing-instruction(5)", null),
                    arrayOf("processing-instruction('asdf','qwer')", null),
                    arrayOf("child::func()", null),
                    // filter expressions
                    arrayOf("bunch-o-nodes()[3]", "{filt-expr:{func-expr:bunch-o-nodes,{}},{{num:3.0}}}"),
                    arrayOf("bunch-o-nodes()[3]['predicates'!='galore']", "{filt-expr:{func-expr:bunch-o-nodes,{}},{{num:3.0},{binop-expr:!=,{str:'predicates'},{str:'galore'}}}}"),
                    arrayOf("(bunch-o-nodes)[3]", "{filt-expr:{path-expr:rel,{{step:child,bunch-o-nodes}}},{{num:3.0}}}"),
                    arrayOf("bunch-o-nodes[3]", "{path-expr:rel,{{step:child,bunch-o-nodes,{{num:3.0}}}}}"),
                    // path steps
                    arrayOf(".", "{path-expr:rel,{{step:self,node()}}}"),
                    arrayOf("..", "{path-expr:rel,{{step:parent,node()}}}"),
                    arrayOf("..[4]", null),
                    arrayOf("preceding::..", null),
                    // name tests
                    arrayOf("name", "{path-expr:rel,{{step:child,name}}}"),
                    arrayOf("qual:name", "{path-expr:rel,{{step:child,qual:name}}}"),
                    arrayOf("a:b:c", null),
                    arrayOf("inv#lid_N~AME", null),
                    arrayOf(".abc", null),
                    arrayOf("5abc", null),
                    arrayOf("_rea--ll:y.funk..y_N4M3", "{path-expr:rel,{{step:child,_rea--ll:y.funk..y_N4M3}}}"),
                    arrayOf("namespace:*", "{path-expr:rel,{{step:child,namespace:*}}}"),
                    arrayOf("*", "{path-expr:rel,{{step:child,*}}}"),
                    arrayOf("*****", "{binop-expr:*,{binop-expr:*,{path-expr:rel,{{step:child,*}}},{path-expr:rel,{{step:child,*}}}},{path-expr:rel,{{step:child,*}}}}"),
                    // axes
                    arrayOf("child::*", "{path-expr:rel,{{step:child,*}}}"),
                    arrayOf("parent::*", "{path-expr:rel,{{step:parent,*}}}"),
                    arrayOf("descendant::*", "{path-expr:rel,{{step:descendant,*}}}"),
                    arrayOf("ancestor::*", "{path-expr:rel,{{step:ancestor,*}}}"),
                    arrayOf("following-sibling::*", "{path-expr:rel,{{step:following-sibling,*}}}"),
                    arrayOf("preceding-sibling::*", "{path-expr:rel,{{step:preceding-sibling,*}}}"),
                    arrayOf("following::*", "{path-expr:rel,{{step:following,*}}}"),
                    arrayOf("preceding::*", "{path-expr:rel,{{step:preceding,*}}}"),
                    arrayOf("attribute::*", "{path-expr:rel,{{step:attribute,*}}}"),
                    arrayOf("namespace::*", "{path-expr:rel,{{step:namespace,*}}}"),
                    arrayOf("self::*", "{path-expr:rel,{{step:self,*}}}"),
                    arrayOf("descendant-or-self::*", "{path-expr:rel,{{step:descendant-or-self,*}}}"),
                    arrayOf("ancestor-or-self::*", "{path-expr:rel,{{step:ancestor-or-self,*}}}"),
                    arrayOf("bad-axis::*", null),
                    arrayOf("::*", null),
                    arrayOf("child::.", null),
                    arrayOf("@attr", "{path-expr:rel,{{step:attribute,attr}}}"),
                    arrayOf("@*", "{path-expr:rel,{{step:attribute,*}}}"),
                    arrayOf("@ns:*", "{path-expr:rel,{{step:attribute,ns:*}}}"),
                    arrayOf("@attr::*", null),
                    // predicates
                    arrayOf("descendant::node()[@attr='blah'][4]", "{path-expr:rel,{{step:descendant,node(),{{binop-expr:==,{path-expr:rel,{{step:attribute,attr}}},{str:'blah'}},{num:4.0}}}}}"),
                    arrayOf("[2+3]", null),
                    // paths
                    arrayOf("rel/ative/path", "{path-expr:rel,{{step:child,rel},{step:child,ative},{step:child,path}}}"),
                    arrayOf("rel/ative/path/", null),
                    arrayOf("/abs/olute/path['etc']", "{path-expr:abs,{{step:child,abs},{step:child,olute},{step:child,path,{{str:'etc'}}}}}"),
                    arrayOf("filter()/expr/path", "{path-expr:{filt-expr:{func-expr:filter,{}},{}},{{step:child,expr},{step:child,path}}}"),
                    arrayOf("fil()['ter']/expr/path", "{path-expr:{filt-expr:{func-expr:fil,{}},{{str:'ter'}}},{{step:child,expr},{step:child,path}}}"),
                    arrayOf("(another-filter)/expr/path", "{path-expr:{filt-expr:{path-expr:rel,{{step:child,another-filter}}},{}},{{step:child,expr},{step:child,path}}}"),
                    arrayOf("filter-expr/(must-come)['first']", null),
                    arrayOf("/", "{path-expr:abs,{}}"),
                    arrayOf("//", null),
                    arrayOf("//all", "{path-expr:abs,{{step:descendant-or-self,node()},{step:child,all}}}"),
                    arrayOf("a/.//../z", "{path-expr:rel,{{step:child,a},{step:self,node()},{step:descendant-or-self,node()},{step:parent,node()},{step:child,z}}}"),
                    arrayOf("6andpath", "{binop-expr:and,{num:6.0},{path-expr:rel,{{step:child,path}}}}"),
                    // real-world examples
                    arrayOf("/patient/sex = 'male' and /patient/age > 15",
                            "{binop-expr:and,{binop-expr:==,{path-expr:abs,{{step:child,patient},{step:child,sex}}},{str:'male'}},{binop-expr:>,{path-expr:abs,{{step:child,patient},{step:child,age}}},{num:15.0}}}"),
                    arrayOf("../jr:hist-data/labs[@type=\"cd4\"]",
                            "{path-expr:rel,{{step:parent,node()},{step:child,jr:hist-data},{step:child,labs,{{binop-expr:==,{path-expr:rel,{{step:attribute,type}}},{str:'cd4'}}}}}}"),
                    arrayOf("function_call(26*(7+3), //*, /im/child::an/ancestor::x[3][true()]/path)",
                            "{func-expr:function_call,{{binop-expr:*,{num:26.0},{binop-expr:+,{num:7.0},{num:3.0}}},{path-expr:abs,{{step:descendant-or-self,node()},{step:child,*}}},{path-expr:abs,{{step:child,im},{step:child,an},{step:ancestor,x,{{num:3.0},{func-expr:true,{}}}},{step:child,path}}}}}")
            )
        }
    }

    @Test
    fun testPathParse() {
        if (expectedParseOutput != null) {
            testXPathValid(inputString, expectedParseOutput)
        } else {
            testXPathInvalid(inputString)
        }
    }

    private fun testXPathValid(expr: String, expected: String) {
        try {
            val xpe = XPathParseTool.parseXPath(expr)
            val result = xpe?.toString()

            if (result == null || result != expected) {
                fail("XPath Parse Failed! Incorrect parse tree." +
                        "\n    expression:[$expr]" +
                        "\n    expected:[$expected]" +
                        "\n    result:  [$result]")
            }

            ExternalizableTest.testExternalizable(ExtWrapTagged(xpe!!), ExtWrapTagged(), LivePrototypeFactory(), "XPath")
        } catch (xse: XPathSyntaxException) {
            fail("XPath Parse Failed! Unexpected syntax error." +
                    "\n    expression:[$expr]")
        }
    }

    private fun testXPathInvalid(expr: String) {
        try {
            val xpe = XPathParseTool.parseXPath(expr)
            val result = xpe?.toString()

            fail("XPath Parse Failed! Did not get syntax error as expected." +
                    "\n    expression:[$expr]" +
                    "\n    result:[${result ?: "(null)"}]")
        } catch (xse: XPathSyntaxException) {
            //success: syntax error as expected
        }
    }
}
