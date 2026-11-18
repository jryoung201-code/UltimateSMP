import { createHash, randomBytes, randomInt, randomUUID } from 'node:crypto';
import { Client, GatewayIntentBits, SlashCommandBuilder, PermissionFlagsBits,
  ActionRowBuilder, ButtonBuilder, ButtonStyle, MessageFlags } from 'discord.js';
import mysql from 'mysql2/promise';

const { DISCORD_TOKEN, DISCORD_GUILD_ID, GIVEAWAY_DATABASE_URL } = process.env;
if (!DISCORD_TOKEN || !DISCORD_GUILD_ID || !GIVEAWAY_DATABASE_URL) {
  throw Error('Set DISCORD_TOKEN, DISCORD_GUILD_ID, and GIVEAWAY_DATABASE_URL.');
}
const database = new URL(GIVEAWAY_DATABASE_URL);
if (database.protocol !== 'mysql:') throw Error('Use a mysql:// database URL.');
const pool = mysql.createPool({
  host: database.hostname, port: Number(database.port || 3306),
  database: decodeURIComponent(database.pathname.slice(1)),
  user: decodeURIComponent(database.username), password: decodeURIComponent(database.password),
  ssl: { rejectUnauthorized: true, ca: process.env.GIVEAWAY_DATABASE_CA?.replaceAll('\\n', '\n') },
  connectionLimit: 4
});
const ranks = ['ultimate-plus', 'ultimate-plus-plus', 'ultimate-plus-plus-plus'];
const keys = ['common', 'uncommon', 'rare', 'legendary'].map(rarity => 'key-' + rarity);
const choices = [...keys, ...ranks].map(value => ({ name: value, value }));
const commands = [
  new SlashCommandBuilder().setName('link').setDescription('Get a private Minecraft linking code'),
  new SlashCommandBuilder().setName('giveaway').setDescription('Start a Minecraft prize giveaway')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
    .addStringOption(option => option.setName('prize').setDescription('Minecraft prize').setRequired(true).addChoices(...choices))
    .addIntegerOption(option => option.setName('quantity').setDescription('Keys per winner, or days of rank access').setRequired(true).setMinValue(1).setMaxValue(365))
    .addIntegerOption(option => option.setName('minutes').setDescription('Time to enter').setRequired(true).setMinValue(1).setMaxValue(43200))
    .addIntegerOption(option => option.setName('winners').setDescription('Number of winners').setRequired(true).setMinValue(1).setMaxValue(20))
];
const client = new Client({ intents: [GatewayIntentBits.Guilds] });
const quiet = { parse: [] };
const hash = code => createHash('sha256').update(code).digest('hex');
const describe = (prize, quantity) => keys.includes(prize)
  ? quantity + ' × ' + prize.slice(4) + ' crate key(s)'
  : prize + ' for ' + quantity + ' day(s)';

async function transaction(work) {
  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();
    const result = await work(connection);
    await connection.commit();
    return result;
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    connection.release();
  }
}

async function link(interaction) {
  const [existing] = await pool.execute('SELECT 1 FROM ultimate_discord_links WHERE discord_id=?', [interaction.user.id]);
  if (existing.length) return 'Your Discord account is already linked.';
  const code = randomBytes(16).toString('hex');
  await pool.execute(
    'INSERT INTO ultimate_discord_codes(discord_id,token_hash,expires) VALUES(?,?,?) ON DUPLICATE KEY UPDATE token_hash=VALUES(token_hash),expires=VALUES(expires)',
    [interaction.user.id, hash(code), Date.now() + 300000]
  );
  return 'Enter `/link ' + code + '` in Minecraft within 5 minutes. Keep this code private.';
}

async function start(interaction) {
  if (!interaction.memberPermissions?.has(PermissionFlagsBits.ManageGuild)) return 'Manage Server permission required.';
  const prize = interaction.options.getString('prize', true);
  const quantity = interaction.options.getInteger('quantity', true);
  const winners = interaction.options.getInteger('winners', true);
  const minutes = interaction.options.getInteger('minutes', true);
  if (!keys.includes(prize) && !ranks.includes(prize)) return 'Unknown prize.';
  if (keys.includes(prize) && quantity > 64) return 'Enter at most 64 keys per winner.';
  const end = Date.now() + minutes * 60000;
  const id = randomUUID();
  const row = new ActionRowBuilder().addComponents(new ButtonBuilder().setCustomId('giveaway:' + id)
    .setLabel('Enter giveaway').setStyle(ButtonStyle.Primary));
  const message = await interaction.channel.send({
    content: '**UltimateSMP giveaway**\n' + describe(prize, quantity) + ' per winner · ' + winners +
      ' winner(s)\nEnds <t:' + Math.floor(end / 1000) + ':R>. Link your Minecraft account with /link to enter.',
    components: [row], allowedMentions: quiet
  });
  try {
    await pool.execute('INSERT INTO ultimate_discord_giveaways(id,guild_id,channel_id,message_id,prize,quantity,winners,ends) VALUES(?,?,?,?,?,?,?,?)',
      [id, interaction.guildId, interaction.channelId, message.id, prize, quantity, winners, end]);
  } catch (error) {
    await message.delete().catch(() => {});
    throw error;
  }
  return 'Giveaway started.';
}

async function enter(interaction) {
  const id = interaction.customId.slice('giveaway:'.length);
  return transaction(async database => {
    const [[giveaway]] = await database.execute(
      'SELECT ends,result FROM ultimate_discord_giveaways WHERE id=? AND guild_id=? AND message_id=? FOR UPDATE',
      [id, interaction.guildId, interaction.message.id]
    );
    if (!giveaway || giveaway.result !== null || Number(giveaway.ends) <= Date.now()) return 'This giveaway is closed.';
    const [linked] = await database.execute('SELECT 1 FROM ultimate_discord_links WHERE discord_id=?', [interaction.user.id]);
    if (!linked.length) return 'Link your Minecraft account with /link first.';
    const [entry] = await database.execute('INSERT IGNORE INTO ultimate_discord_entries(giveaway_id,discord_id) VALUES(?,?)',
      [id, interaction.user.id]);
    return entry.affectedRows ? 'You entered the giveaway.' : 'You already entered this giveaway.';
  });
}

async function settle(id) {
  return transaction(async database => {
    const [[giveaway]] = await database.execute('SELECT * FROM ultimate_discord_giveaways WHERE id=? FOR UPDATE', [id]);
    if (!giveaway || giveaway.announced || Number(giveaway.ends) > Date.now()) return null;
    if (giveaway.result !== null) return giveaway;
    const [entries] = await database.execute(
      'SELECT e.discord_id,l.player_id FROM ultimate_discord_entries e JOIN ultimate_discord_links l ON e.discord_id=l.discord_id WHERE e.giveaway_id=?', [id]);
    const winners = [];
    const now = Date.now();
    for (let index = 0; index < giveaway.winners && entries.length; index++) {
      const [winner] = entries.splice(randomInt(entries.length), 1);
      const source = 'giveaway:' + id + ':' + winner.discord_id;
      if (keys.includes(giveaway.prize)) {
        await database.execute("INSERT INTO ultimate_shop_key_deliveries(id,source,player_id,package_id,amount,state,updated_at) VALUES(?,?,?,?,?,'pending',?)",
          [randomUUID(), source, winner.player_id, giveaway.prize, giveaway.quantity, now]);
      } else {
        await database.execute('INSERT INTO ultimate_shop_grants(source,player_id,package_id,expires) VALUES(?,?,?,?)',
          [source, winner.player_id, giveaway.prize, now + giveaway.quantity * 86400000]);
      }
      winners.push(winner.discord_id);
    }
    giveaway.result = JSON.stringify(winners);
    await database.execute('UPDATE ultimate_discord_giveaways SET result=? WHERE id=?', [giveaway.result, id]);
    return giveaway;
  });
}

let checking = false;
async function finishDue() {
  if (checking) return;
  checking = true;
  try {
    const [due] = await pool.execute('SELECT id FROM ultimate_discord_giveaways WHERE guild_id=? AND announced=0 AND ends<=? ORDER BY ends LIMIT 25',
      [DISCORD_GUILD_ID, Date.now()]);
    for (const { id } of due) {
      try {
        const giveaway = await settle(id);
        if (!giveaway) continue;
        const winners = JSON.parse(giveaway.result);
        try {
          const channel = await client.channels.fetch(giveaway.channel_id);
          const message = await channel.messages.fetch(giveaway.message_id);
          await message.edit({
            content: '**UltimateSMP giveaway ended**\n' + describe(giveaway.prize, giveaway.quantity) +
              (winners.length ? '\nWinners: ' + winners.map(user => '<@' + user + '>').join(', ') +
                (keys.includes(giveaway.prize) ? '\nKeys arrive in Minecraft when you join with inventory space.' : '\nRank access starts at the draw.') : '\nNo eligible entries.'),
            components: [], allowedMentions: quiet
          });
        } catch (error) {
          if (![10003, 10008].includes(Number(error.code))) throw error;
          console.warn('Giveaway prizes were processed, but the Discord post or channel was deleted.');
        }
        await pool.execute('UPDATE ultimate_discord_giveaways SET announced=1 WHERE id=?', [id]);
      } catch (error) {
        console.warn('Giveaway processing failed; will retry.', error.code || error.name);
      }
    }
  } catch (error) {
    console.warn('Giveaway database unavailable; will retry.', error.code || error.name);
  } finally {
    checking = false;
  }
}

client.on('interactionCreate', async interaction => {
  if (interaction.guildId !== DISCORD_GUILD_ID) return;
  const name = interaction.isChatInputCommand() ? interaction.commandName : '';
  const entry = interaction.isButton() && interaction.customId.startsWith('giveaway:');
  if (!entry && name !== 'link' && name !== 'giveaway') return;
  try {
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });
    const response = entry ? await enter(interaction) : name === 'link' ? await link(interaction) : await start(interaction);
    await interaction.editReply({ content: response, allowedMentions: quiet });
  } catch (error) {
    console.warn('Discord command failed.', error.code || error.name);
    if (interaction.deferred) await interaction.editReply('Temporarily unavailable. Try again shortly.').catch(() => {});
  }
});
client.once('clientReady', async ready => {
  try {
    const guild = await ready.guilds.fetch(DISCORD_GUILD_ID);
    const registered = await guild.commands.fetch();
    for (const command of commands) {
      const existing = registered.find(entry => entry.name === command.name);
      if (existing) await guild.commands.edit(existing.id, command.toJSON());
      else await guild.commands.create(command.toJSON());
    }
    console.log('UltimateSMP Discord bot ready.');
    void finishDue();
    setInterval(finishDue, 10000);
  } catch (error) {
    console.error('Discord command registration failed.', error.code || error.name);
    process.exit(1);
  }
});
await client.login(DISCORD_TOKEN).catch(() => {
  console.error('Discord login failed. Check the bot token and network connection.');
  process.exit(1);
});
