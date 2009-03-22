package org.hypergraphdb.peer;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import net.jxta.document.AdvertisementFactory;
import net.jxta.id.IDFactory;
import net.jxta.protocol.PipeAdvertisement;

import org.hypergraphdb.HGHandleFactory;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.handle.HGLiveHandle;
import org.hypergraphdb.handle.PhantomHandle;
import org.hypergraphdb.handle.PhantomManagedHandle;
import org.hypergraphdb.handle.UUIDPersistentHandle;
import org.hypergraphdb.peer.log.Timestamp;
import org.hypergraphdb.peer.serializer.CustomSerializedValue;
import org.hypergraphdb.query.And;
import org.hypergraphdb.query.AnyAtomCondition;
import org.hypergraphdb.query.ArityCondition;
import org.hypergraphdb.query.AtomPartCondition;
import org.hypergraphdb.query.AtomProjectionCondition;
import org.hypergraphdb.query.AtomTypeCondition;
import org.hypergraphdb.query.AtomValueCondition;
import org.hypergraphdb.query.BFSCondition;
import org.hypergraphdb.query.DFSCondition;
import org.hypergraphdb.query.HGAtomPredicate;
import org.hypergraphdb.query.HGQueryCondition;
import org.hypergraphdb.query.IncidentCondition;
import org.hypergraphdb.query.LinkCondition;
import org.hypergraphdb.query.MapCondition;
import org.hypergraphdb.query.Not;
import org.hypergraphdb.query.Nothing;
import org.hypergraphdb.query.Or;
import org.hypergraphdb.query.OrderedLinkCondition;
import org.hypergraphdb.query.SubsumedCondition;
import org.hypergraphdb.query.SubsumesCondition;
import org.hypergraphdb.query.TargetCondition;
import org.hypergraphdb.query.TypePlusCondition;
import org.hypergraphdb.query.TypedValueCondition;
import org.hypergraphdb.query.impl.LinkProjectionMapping;
import org.hypergraphdb.type.BonesOfBeans;
import org.hypergraphdb.util.Pair;


/**
 * 
 * <p>
 * Utility methods to be used in constructing nested structures for complex
 * message representations. This class consists entirely of static methods
 * and is designed to be imported with <code>import org.hypergraphdb.peer.Structs.*</code>.
 * </p>
 *
 * @author Borislav Iordanov
 *
 */
@SuppressWarnings("unchecked")
public class Structs
{	
	/**
	 * <p>Return primitives, lists and maps <code>as-is</code>, transform collections
	 * to lists and beans to structs (i.e. String->Value maps). Special case: 
	 * instances of either <code>HGQueryCondition</code> or <code>HGAtomPredicate</code>
	 * or passed to <code>hgQueryOrPredicate</code> to create an appropriate
	 * representation.</p>
	 */
	public static Object svalue(Object x)
	{
		return svalue(x, false, true, null);

	}
	/**
	 * <p>Use reflection to create a map of the bean properties of the argument.</p>
	 */
	public static Map<String, Object> struct(Object bean)
	{
		return struct(bean, false);
	}
	
	
	/**
	 * <p>Create a record-like structure of name value pairs as a regular Java
	 * <code>Map<String, Object</code>. The method takes a variable number of arguments
	 * where each argument at an even position must be a name with the argument following
	 * it its value.</p>
	 * 
	 * <p>For example: <code>struct("personName", "Adriano Celentano", "age", 245)</code>.</p>
	 */
	public static Map<String, Object> struct(Object...args)
	{		
		if (args == null)
			return null;
		Map<String, Object> m = new HashMap<String, Object>();		
		if (args.length % 2 != 0)
			throw new IllegalArgumentException("The arguments array to struct must be of even size: a flattened list of name/value pairs");
		for (int i = 0; i < args.length; i+=2)
		{
			if (! (args[i] instanceof String) )
				throw new IllegalArgumentException("An argument at the even position " + i + " is not a string.");
			m.put((String)args[i], svalue(args[i+1], false, true, null));
		}
		return m;
	}
	
	/**
	 * <p>Create a Java list out of a list of arguments.</p>
	 */
	public static List<Object> list(Object...args)
	{
		List<Object> l = new ArrayList<Object>();
		if (args == null)
			return l;
		else for (Object x : args)
			l.add(svalue(x));
		return l;
	}
	
	/**
	 * Creates an object that will be serialized via the custom mechanism.
	 * 
	 * @param value
	 * @return
	 */
	public static Object object(Object value)
	{
		return new CustomSerializedValue(value);
	}

	private static Object svalue(Object x, boolean skipSpecialClasses, boolean addClassName, Class<?> propertyType)
	{
		// skipSpecialClasses is set to true when the content of the HGQueryCondition/HGAtomPredicate should be rendered
		if ((!skipSpecialClasses) && (x instanceof HGQueryCondition || x instanceof HGAtomPredicate))
			return hgQueryOrPredicate(x);
		else if (x instanceof CustomSerializedValue) return x;
		else if (x == null || 
			x instanceof Boolean || 
			x instanceof String ||
			x instanceof Map)
			return x;
		else if (x instanceof Number)
		{
			//might be a property that gives no information on what needs to be created.
			if ((propertyType != null) && (!x.getClass().isAssignableFrom(propertyType)))
			{
				String typeName = getNumberType(x.getClass());
				if (typeName == null) return x;
				else return list(getNumberType(x.getClass()), x);
			}else return x;
		}
		else if (hgMappers.containsKey(x.getClass()))
		{
			//certain objects do not expose bean like interfaces but still need to be serialized.
			Pair<StructsMapper, String> mapper = hgMappers.get(x.getClass());
			return list(mapper.getSecond(), mapper.getFirst().getStruct(x));
		}
		else if (hgClassNames.containsKey(x.getClass()))
		{
			//beans with short names for their type
			if (!addClassName) 
			{
				if (x instanceof List) return x;
				else return struct(x, false);
			}
			else 
			{
				if (x instanceof List) 
				{
					ArrayList<Object> l = new ArrayList<Object>();
					for(Object i:(List<?>)x)
					{
						l.add(svalue(i, false, addClassName, null));
					}
					return list(hgClassNames.get(x.getClass()), l);
				}
				else return list(hgClassNames.get(x.getClass()), struct(x, true));	
			}
		}
		else if (x.getClass().isArray())
		{
			ArrayList<Object> l = new ArrayList<Object>();
			for(int i=0;i<Array.getLength(x);i++)
			{
				l.add(svalue(Array.get(x, i), false, addClassName, null));
			}
			return l;
		}
		else if (x instanceof Enum) return x.toString();
		else if (x instanceof Class) return ((Class<?>)x).getName();
		else if (x instanceof List)
		{
			ArrayList<Object> l = new ArrayList<Object>();
			for(Object i:(List<?>)x)
			{
				l.add(svalue(i, false, addClassName, null));
			}
			return l;
		}
		else if (x instanceof Collection)
		{
			ArrayList<Object> l = new ArrayList<Object>();
			for(Object i:(Collection<?>)x)
			{
				l.add(svalue(i, false, addClassName, null));
			}
			return l;
		}
		else
		{
			if (!addClassName) return struct(x, false);
			else return list(hgClassNames.get(x.getClass()), struct(x, true));
		}
			
	}

	private static String getNumberType(Class<?> numberClass)
	{
		if (numberClass.equals(int.class) || numberClass.equals(Integer.class)) return "int";
		else if (numberClass.equals(short.class) || numberClass.equals(Short.class)) return "short";
		else if (numberClass.equals(byte.class) || numberClass.equals(Byte.class)) return "byte";

		return null;
	}
	private static Number getNumber(List<?> list)
	{
		String typeName = list.get(0).toString();
		
		if ("int".equals(typeName)) return ((Long)list.get(1)).intValue();
		else if ("short".equals(typeName)) return ((Long)list.get(1)).shortValue();
		else if ("byte".equals(typeName)) return ((Long)list.get(1)).byteValue();
		else return null;
	}
	private static Map<String, Object> struct(Object bean, boolean addClassName)
	{
		if (bean == null)
			return null;
		Map<String, Object> m = new HashMap<String, Object>();
		for (PropertyDescriptor desc : BonesOfBeans.getAllPropertyDescriptors(bean.getClass()).values())
		{
			if (desc.getReadMethod() != null && desc.getWriteMethod() != null)
				m.put(desc.getName(), svalue(BonesOfBeans.getProperty(bean, desc), false, addClassName, desc.getPropertyType()));
		}
		return m;
	}	
	

	
//	private static TwoWayMap<Class<?>, String> hgClassNames = new TwoWayMap<Class<?>, String>();
	private static Map<Class<?>, String> hgClassNames = new HashMap<Class<?>, String>();
	private static Map<String, Class<?>> hgInvertedClassNames = new HashMap<String, Class<?>>();
	private static Map<Class<?>, Pair<StructsMapper, String>> hgMappers = new HashMap<Class<?>, Pair<StructsMapper, String>>();
	private static Map<String, StructsMapper> hgInvertedMappers = new HashMap<String, StructsMapper>();

	static
	{
		hgClassNames.put(And.class, "and");
		hgClassNames.put(AnyAtomCondition.class, "any");
		hgClassNames.put(ArityCondition.class, "arity");
		hgClassNames.put(AtomPartCondition.class, "part");
		hgClassNames.put(AtomProjectionCondition.class, "proj");
		hgClassNames.put(AtomTypeCondition.class, "type");
		hgClassNames.put(AtomValueCondition.class, "value");
		hgClassNames.put(BFSCondition.class, "bfs");
		hgClassNames.put(DFSCondition.class, "dfs");
		hgClassNames.put(IncidentCondition.class, "incident");
		hgClassNames.put(Not.class, "not");
		hgClassNames.put(Or.class, "or");
		hgClassNames.put(OrderedLinkCondition.class, "orderedLink");
		hgClassNames.put(SubsumedCondition.class, "subsumed");
		hgClassNames.put(SubsumesCondition.class, "subsumes");	
		hgClassNames.put(TargetCondition.class, "target");	
		hgClassNames.put(TypedValueCondition.class, "typedValue");	
		hgClassNames.put(TypePlusCondition.class, "typePlus");
		hgClassNames.put(Nothing.class, "nothing");
		
		hgClassNames.put(MapCondition.class, "mapCond");
		hgClassNames.put(LinkProjectionMapping.class, "linkProj");
		
		hgClassNames.put(Timestamp.class, "time");
		
		hgClassNames.put(LinkCondition.class, "link");
		
		for(Entry<Class<?>, String> entry : hgClassNames.entrySet())
		{
			hgInvertedClassNames.put(entry.getValue(), entry.getKey());
		}
		
		addMapper(UUID.class, new UUIDStructsMapper(), "uuid");
		addMapper(UUIDPersistentHandle.class, new HandleMapper(), "persistent-handle");
		addMapper(PhantomManagedHandle.class, new HandleMapper(), "live-managed-handle");
		addMapper(PhantomHandle.class, new HandleMapper(), "live-handle");
		addMapper(net.jxta.impl.protocol.PipeAdv.class, new PipeAdvStructsMapper(), "pipe");
//		addMapper(Subgraph.class, new SubGraphMapper(), "storage-graph");
		
	}
	
	/**
	 * Adds a StructsMapper for a specific class. Mappers are capable of creating a struct from an 
	 * object of the given class.
	 * @param clazz
	 * @param mapper
	 * @param name
	 */
	public static void addMapper(Class<?> clazz, StructsMapper mapper, String name)
	{
		hgMappers.put(clazz, new Pair<StructsMapper, String>(mapper, name));
		hgInvertedMappers.put(name, mapper);
	}
	
	public static HGQueryCondition getHGQueryCondition(Object value, Object...args)
	{
		Object result = getPart(value, args);
		if (result instanceof HGQueryCondition) return (HGQueryCondition)result;
		else return null;
	}
	public static HGAtomPredicate getHGAtomPredicate(Object value, Object...args)
	{
		Object result = getPart(value, args);
		if (result instanceof HGAtomPredicate) return (HGAtomPredicate)result;
		else return null;
	}
	
	/**
	 * Gets a part of the struct. The path to the element to be retrieved is given as a sequence of 
	 * objects (int for lists, strings for maps). If at the end of the path we have a representation of 
	 * a bean we recreate the bean, if not we just return the object.
	 * 
	 * @param source
	 * @param args
	 * @return
	 */
	public static <T> T getPart(Object source, Object...args)
	{
		if (args == null) return null;
		
		if (args.length == 0) return (T)createObject(source);
		if (args.length == 1) return (T)getStructPart(source, args[0]);
		else 
		{
			List<Object> l = new ArrayList<Object>();
			for (Object x : args) l.add(x);
	
			return (T)getStructPart(source, l, 0);
		}
	}

	public static Map<String, Object> getStruct(Object source, Object...args)
	{
		return (Map<String, Object>)getPart(source, args);
	}
	
	public static boolean hasPart(Object source, Object...args)
	{
		if ((args == null) || (source == null)) return false;
		
		if (args.length == 0) return true;
		if (args.length == 1) return hasStructPart(source, args[0]);
		else 
		{
			List<Object> l = new ArrayList<Object>();
			for (Object x : args) l.add(x);
	
			return hasStructPart(source, l, 0);
		}
	}
	public static <T> T getOptPart(Object source, T defaultValue, Object...args)
	{
		if (source == null) return defaultValue;		
		if (hasPart(source, args)) 
			defaultValue = (T)getPart(source, args);
		return defaultValue;
	}
	private static boolean hasStructPart(Object source, Object path, int pos)
	{
		if (!(path instanceof List))
		{
			return hasStructPart(source, path);
		}else{
			List<Object> list = (List<Object>)path;
			if ((list.size() < pos) || (pos < 0)) return false;
			
			boolean hasPart = hasStructPart(source, list.get(pos));
			if (hasPart)
			{
				if (pos == list.size()) return true;
				else return hasStructPart(getStructPart(source, list.get(pos)), path, pos + 1);
			}else return false;
		}
	}
	private static Object getStructPart(Object source, Object path, int pos)
	{
		if (!(path instanceof List))
		{
			return getStructPart(source, path);		
		}
		else
		{
			List<Object> list = (List<Object>)path;
			if ((list.size() < pos) || (pos < 0)) return null;
			
			Object part = getStructPart(source, list.get(pos));
			pos++;
			
			if (pos == list.size()) return createObject(part);
			else return getStructPart(part, path, pos);
		}
	}
	
	private static boolean hasStructPart(Object source, Object position)
	{
		if (source instanceof Map){
			return ((Map)source).containsKey(position.toString());
		}
		else if (source instanceof List)
		{
			Integer listPos = (Integer)position;
			return (listPos >= 0) && (listPos < ((List)source).size());
		}
		else 
			return false;		
	}
	
	private static Object getStructPart(Object source, Object position)
	{
		if (source instanceof Map){
			return createObject(((Map)source).get(position.toString()));
		}else if (source instanceof List)
		{
			return createObject(((List)source).get((Integer)position));
		}else return createObject(source);
	}
	
	/**
	 * Tries to create an object from a source. Assumes source is a CustomSerializedValue or a list of two elements
	 * first being the type name and the second the content of the object.
	 * @param source
	 * @return
	 */
	private static Object createObject(Object source)
	{
		if (source == null) return null;
		
		if (source instanceof CustomSerializedValue)
		{
			return ((CustomSerializedValue)source).get();
		}
		else if (source instanceof List)
		{
			List<Object> data = (List<Object>)source;
			
			if ((data.size() == 2) && (data.get(0) instanceof String))
			{
				//could be an different object
				String className = (String)data.get(0);
				
				if (hgInvertedMappers.containsKey(className))
				{
					return hgInvertedMappers.get(className).getObject(data.get(1));
				}
				else 
				{
					Number num = getNumber(data);
					if (num != null) 
						return num;
					else
					{
						Class<?> clazz = getBeanClass(className);
						if (clazz == null) return source;
						else return createObject(data, clazz);
					}
				}
			}
			else 
			{
				List<Object> result = new ArrayList<Object>();
				for(Object o : data)
				{
					result.add(createObject(o));
				}					
				return result;
			}			
		}
		else 
			return source;
	}
	
	private static Class<?> getBeanClass(String className)
	{
		//must be a list of two elements, the first being the class name
		Class<?> result = null;
		
		result = hgInvertedClassNames.get(className);
		if (result == null)
		{
			//try to find the class
			try
			{
				result = Class.forName(className);
			} catch (ClassNotFoundException e)
			{
				//do nothing ... maybe not a bean
			}
		}
		
		return result;
	}
	
	private static Object createObject(List<Object> data, Class<?> clazz)
	{
		Object result = null;
		try
		{
			result = clazz.newInstance();
			//result might be a list or a bean ... 
			if (data.get(1) instanceof Map)
			{
				//load bean properties
				Map<String, Object> properties = (Map<String, Object>)data.get(1);
				loadMapValues(result, properties);
			}else{
				//load the values
				if (result instanceof List)
				{
					List<Object> values = (List<Object>)data.get(1);
					loadListValues((List<Object>)result, values);
				}
			}

		} catch (InstantiationException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return result;
	}

	public static void loadListValues(List<Object> destination, List<Object> source)
	{
		for(Object x : source)
		{
			destination.add(createObject(x));
		}
	}
	public static void loadMapValues(Object bean, Map<String, Object> properties)
	{
		for(Entry<String, Object> entry : properties.entrySet())
		{
			PropertyDescriptor descriptor = BonesOfBeans.getPropertyDescriptor(bean, entry.getKey());
			Class propertyClass = descriptor.getPropertyType();

			Object value = createObject(entry.getValue());
			if (value instanceof Long) value = getValueForProperty(propertyClass, (Long)value);
			else if ((value instanceof ArrayList) && (!propertyClass.isAssignableFrom(value.getClass())))
					value = getValueForProperty(propertyClass, (ArrayList)value);
			else if (propertyClass.isEnum())
				value = Enum.valueOf(propertyClass, value.toString());
			else if (propertyClass.equals(Class.class))
			{
				if (value != null)
				{
					try
					{
						value = Class.forName(value.toString());
					} catch (ClassNotFoundException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			BonesOfBeans.setProperty(bean, entry.getKey(), value);
		}
	}
	
	/**
	 * Casts the value of the second parameter to the type of the property.
	 * @param propertyClass
	 * @param number
	 * @return
	 */
	private static Object getValueForProperty(Class<?> propertyClass, Long number)
	{
		if (propertyClass.equals(Long.class) || propertyClass.equals(long.class))
		{
			return number;
		}else if (propertyClass.equals(Integer.class) || propertyClass.equals(int.class))
		{
			return number.intValue();
		}else if (propertyClass.equals(Short.class) || propertyClass.equals(short.class))
		{
			return number.shortValue();
		}else if (propertyClass.equals(Byte.class) || propertyClass.equals(byte.class))
		{
			return number.byteValue();
		}
		
		return number;
	}
	
	/**
	 * Casts the value of the second parameter to the type of the property.
	 * 
	 * @param propertyClass
	 * @param list
	 * @return
	 */
	private static Object getValueForProperty(Class<?> propertyClass, ArrayList list)
	{
		if(propertyClass.isArray()){
			Object array = Array.newInstance(propertyClass.getComponentType(), list.size());
			for(int i =0;i<list.size();i++)
			{
				Object elem = createObject(list.get(i));
				Array.set(array, i, elem);
			}
			return array;
		}else if (Collection.class.isAssignableFrom(propertyClass)){
			if (propertyClass.isAssignableFrom(ArrayList.class)) return list;
			else
			{
				Collection col = null;
				try
				{
					col = (Collection) propertyClass.newInstance();
					for(Object x:list) col.add(createObject(x));
				} catch (InstantiationException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return col;
			}
		}else{
			return createObject(list);
		}

	}


	
	public static List<Object> hgQueryOrPredicate(Object x)
	{
		if (x == null)
			return null;
		String name = hgClassNames.get(x.getClass());

		if (name == null)
			throw new IllegalArgumentException(
				"Unknown HyperGraph query condition or atom predicate type '" + x.getClass().getName() + "'");
		
		//return list(name, svalue(x, true, true));
		return (List<Object>) svalue(x, true, true, null);
	}
	
	public static List<Object> hgQuery(HGQueryCondition condition)
	{
		return hgQueryOrPredicate(condition);
	}
	
	public static List<Object> hgPredicate(HGAtomPredicate predicate)
	{
		return hgQueryOrPredicate(predicate);
	}
	
	public static Map<String, Object> merge(Map<String, Object> m1, Map<String, Object> m2)
	{
		Map<String, Object> m = new HashMap<String, Object>();
		if (m1 != null)
			m.putAll(m1);
		if (m2 != null)
			m.putAll(m2);
		return m;
	}
	
	public List<Object> append(List<Object> l1, List<Object> l2)
	{
		List<Object> l = new ArrayList<Object>();
		if (l1 != null)
			l.addAll(l1);
		if (l2 != null)
			l.addAll(l2);
		return l;
	}
	
	public static Message combine(Message msg, Map<String, Object> s)
	{
	    msg.putAll(s);
	    return msg;
	}
	
	/**
	 * <p>
	 * Merge the second argument into the first and return the latter. If the 
	 * arguments are both maps or both lists, all entries from the second are 
	 * stored in the first. Otherwise, nothing is done.
	 * </p>
	 * @param o1 The target of the merge.
	 * @param o2 The source of the merge.
	 * @return The possibly modified <code>o1</code>.
	 */
	public static <T> T combine(T o1, T o2)
	{
		if (o1 instanceof Map)
		{
			if (o2 instanceof Map)
			{
				((Map)o1).putAll((Map)o2);
			}
		}else if (o1 instanceof List)
		{
			if (o2 instanceof List)
			{
				((List)o1).addAll((List)o2);
			}else{
				((List)o1).add(o2);
			}
		}
		return o1;
	}
	
	/**
	 * @author ciprian.costa
	 * Implementors provide functions to create a struct from an object and an object from a struct.
	 *
	 */
	public static interface StructsMapper
	{
		Object getStruct(Object value);
		Object getObject(Object struct);
	}
/*	
	public static class SubGraphMapper implements StructsMapper
	{
        public Object getObject(Object struct)
        {
            ArrayList<Object> data = (ArrayList<Object>)struct;
            return new UUID((Long)data.get(0), (Long)data.get(1));
        }

        public Object getStruct(Object value)
        {
            Subgraph sg = (Subgraph)value;
            return list(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
        }	    
	}
	*/
	/**
	 * @author ciprian.costa
	 *
	 * Mapper for UUID
	 */
	public static class UUIDStructsMapper implements StructsMapper
	{
		public Object getObject(Object struct)
		{
			ArrayList<Object> data = (ArrayList<Object>)struct;
			return new UUID((Long)data.get(0), (Long)data.get(1));
		}

		public Object getStruct(Object value)
		{
			UUID uuid = (UUID)value;
			return list(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
		}
	}

    public static class HandleMapper implements StructsMapper
    {
        public Object getObject(Object struct)
        {
            return HGHandleFactory.makeHandle(struct.toString());
        }

        public Object getStruct(Object value)
        {            
            if (value instanceof HGPersistentHandle)
                return value.toString();
            else if (value instanceof HGLiveHandle)
                return ((HGLiveHandle)value).getPersistentHandle().toString();
            else
                throw new RuntimeException("Attempt to serialize something that is not a HG handle as a HG handle.");
        }
    }
    
	/**
	 * @author ciprian.costa
	 *
	 * Mapper for pipe advertisements.
	 */
	public static class PipeAdvStructsMapper implements StructsMapper
	{
		public Object getObject(Object struct)
		{
			
			PipeAdvertisement adv = (PipeAdvertisement)AdvertisementFactory.newAdvertisement(PipeAdvertisement.getAdvertisementType());
			
			Object x = getPart(struct, "type"); // variable needed because of Java 5 compiler bug
			adv.setType(x.toString());
			x = getPart(struct, "name");
			adv.setName(x.toString());
			try
			{
			    x = getPart(struct, "id");
				adv.setPipeID(IDFactory.fromURI(URI.create(x.toString())));
			} catch (URISyntaxException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return adv;
		}

		public Object getStruct(Object value)
		{
			PipeAdvertisement adv = (PipeAdvertisement)value;
			
			return struct("type", adv.getType(), "id", adv.getPipeID().toString(), "name", adv.getName());
		}
		
	}
}