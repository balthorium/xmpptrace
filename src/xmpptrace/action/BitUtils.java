package xmpptrace.action;

/**
 * Class to provide utility functions for manipulating bits in a char array,
 * converting to strings (for display), converting to integers, etc.  This
 * was mainly motivated by the need to read non-standard lengths of bit fields
 * from IP and TCP headers and convert to usable Java integer types.
 * 
 * @author adb
 *
 */
public class BitUtils
{
    public long mValue;
    
    @SuppressWarnings("serial")
	public static class BitUtilsException extends Exception {};
    
    public enum ByteOrder
    {
        BIG_ENDIAN,
        LITTLE_ENDIAN
    }

    /**
     * If the offset'th bit of the given byte is a 1, this will append a 1
     * to the given string buffer.  Otherwise will append a 0.
     * @param str The buffer to which a 1 or 0 is to be appended.
     * @param b The byte to be inspected.
     * @param offset The offset of the bit to be inspected. A 0 indicates the
     *        LSB, a 7 indicates the MSB.
     */
    public static void bitToString(StringBuffer str, byte b, int offset)
    {
        byte mask = (byte)((byte)0x01 << offset);
        if ((b & mask) != 0)
        {
            str.append("1");
        }
        else
        {
            str.append("0");
        }
    }

    /**
     * Reads the given byte array, and produces a binary string representation
     * of its values.
     * @param str The string to which the binary representation shall be written.
     * @param b The Array of bytes to be read.
     * @param offset The array index at which to begin reading data from the array.
     * @param count The number of bytes to read from the array.
     */
    public static void bitsToString(StringBuffer str, byte[] b, int offset, int count)
    {
        for (int i = offset; i < offset + count; ++i)
        {
            bitToString(str, b[i/8], i%8);
            if (i > offset && (i+1)%8 == 0)
            {
                str.append(" ");
            }
            if (i > offset && (i+1)%32 == 0)
            {
                str.append("\n");
            }
        }
    }

    /**
     * Reads the given byte array, and produces a hexidecimal string representation
     * of its values.
     * @param str The string to which the hex representation shall be written.
     * @param b The Array of bytes to be read.
     * @param offset The array index at which to begin reading data from the array.
     * @param count The number of bytes to read from the array.
     */
    public static void bytesToString(StringBuffer str, byte[] b, int offset, int count)
    {
        for (int i = offset; i < offset + count; ++i)
        {
            str.append(String.format("%02X ", b[i]));
            if (i > offset && ((i+1)%4 == 0))
            {
                str.append("\n");
            }
        }
    }
    
    /**
     * Interprets an integral number of bytes to an unsigned integer (long).
     * @param b Array containing the bytes to be interpreted.
     * @param offset Index in b of the first byte of interest.
     * @param size Number of bytes to be interpreted (0 &lt; size &lt; 8).
     * @param bo The byte order (big endian or little endian).
     * @return A positive integral value represented by the array bytes (-1 on error).
     */
    public static long bytesToLong(
            byte[] b, int offset, int size, ByteOrder bo) 
    {
        if ((size > 7) || (offset + size > b.length))
        {
            return -1;
        }
        
        long result = 0;
        for (int i = offset; i < offset + size; ++i)
        {
            // this bit of insanity stems from java's PITA casting rules...
            long l = (b[i] >= 0) ? b[i] : (b[i] &= 0x7F) + 0x80;
            
            result += (bo == ByteOrder.BIG_ENDIAN) ?
                    l << 8 * (size - 1 - (i - offset)) :
                    l << 8 * (i - offset);
        }
        
        return result;
    }        
}


    
