package test.net.sourceforge.pmd;

import junit.framework.*;
import java.util.*;

import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleViolation;

public class RuleViolationTest extends TestCase {
    public RuleViolationTest(String name) {
        super(name);
    }
    
    public void testBasic() {
        RuleViolation r = new RuleViolation(new Rule() {
            public String getName() {return "name";}
            public String getDescription()  {return "desc";}
        }, 2);
        assertTrue(r.getText().indexOf("name") != -1);
    }

    public void testGetText() {
        RuleViolation r = new RuleViolation(new Rule() {
            public String getName() {return "name";}
            public String getDescription()  {return "desc";}
        }, 2, "foo");
        assertTrue(r.getText().indexOf("foo") != -1);
    }

    public void testXML() {
        RuleViolation r = new RuleViolation(new Rule() {
            public String getName() {return "name";}
            public String getDescription()  {return "desc";}
        }, 2, "foo");
        assertTrue(r.getXML().indexOf("foo") != -1);
        assertTrue(r.getXML().indexOf("2") != -1);
    }
}
